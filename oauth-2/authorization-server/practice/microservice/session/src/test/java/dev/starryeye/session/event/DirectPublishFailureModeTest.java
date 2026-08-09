package dev.starryeye.session.event;

import dev.starryeye.session.SessionService;
import dev.starryeye.session.jpa.OidcSessionEntityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@SpringBootTest
class DirectPublishFailureModeTest {

	/**
	 * 직접 발행이 남기는 두 실패를 현재 동작 그대로 고정한다. 고치지 않는다 —
	 *      Task 7 에서 outbox 로 바꿀 때 이 테스트들이 반대 결과를 요구하도록 뒤집는 것이 목적이다.
	 *
	 * 주의. DB 트랜잭션과 Kafka 전송은 서로 다른 시스템이라 하나의 원자 단위가 될 수 없다.
	 *      순서를 어떻게 잡든 사이에 틈이 남는다.
	 */

	@Autowired
	private SessionService sessionService;

	@Autowired
	private OidcSessionEntityRepository repository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@MockitoSpyBean
	private LogoutEventPublisher publisher;

	@Test
	@DisplayName("[현재 동작] Kafka 발행이 실패하면 로그아웃이 통째로 롤백된다 — 세션 행이 그대로 남는다")
	void publishFailureRollsBackTheWholeLogout() {
		sessionService.register("SID-A", "user-sub-0001", "my-client");
		doThrow(new IllegalStateException("kafka down")).when(publisher).publish(anyString(), any());

		assertThatThrownBy(() -> sessionService.consumeForLogout("SID-A"))
				.isInstanceOf(IllegalStateException.class);

		// Kafka 장애가 로그아웃 장애가 됐다. auth 는 이 실패를 fail-open 으로 삼키므로 사용자에게는
		// 로그아웃된 것처럼 보이는데, 서버에는 아무 일도 일어나지 않았다 — 세션 행도 남고 refresh 도 산다.
		assertThat(repository.findBySid("SID-A")).hasSize(1);
	}

	@Test
	@DisplayName("[현재 동작] 발행은 DB 커밋 전에 일어난다 — 그 순간 밖에서 보면 세션 행이 아직 살아 있다")
	void publishHappensBeforeCommit() {
		sessionService.register("SID-B", "user-sub-0001", "my-client");

		// verify(단순 호출 여부)만으로는 "언제" 불렸는지 모른다 — consumeForLogout 이 반환된 뒤(=
		// @Transactional 프록시가 커밋까지 끝낸 뒤)에 봐도 통과해 버린다.
		//
		// TransactionSynchronizationManager.isActualTransactionActive() 로 시점을 잡는 방법도 시도했지만
		// 안 통한다 — Spring 은 afterCommit 콜백을 cleanupAfterCompletion(스레드로컬을 정리해 이 플래그를
		// 끄는 지점)보다 먼저 실행한다(AbstractPlatformTransactionManager.processCommit, spring-tx 소스
		// 833~847행: triggerAfterCommit 이 바깥 finally 의 cleanupAfterCompletion 보다 먼저 온다). 즉
		// publish 를 afterCommit 훅으로 옮겨도 그 안에서는 여전히 isActualTransactionActive()==true 다.
		// 실제로 그 변이를 돌려 이 방법이 못 잡는다는 것을 확인했다(보고서 참고).
		//
		// 그래서 "이 스레드가 트랜잭션 안에 있다고 스스로 믿는지" 대신 "DB 커밋이 이미 밖에서 보이는지"
		// 를 직접 잰다 — publish 호출 시점에 완전히 별도의 REQUIRES_NEW 트랜잭션(별도 커넥션)으로 같은
		// 행을 읽어, 아직 지워지지 않은 채로 보이는지 확인한다. 격리 수준(H2 기본 READ_COMMITTED, MVCC)
		// 상 미커밋 delete 는 다른 커넥션에 보이지 않으므로, "밖에서 아직 보인다"는 곧 "아직 커밋 전"
		// 이라는 뜻이다. 이 스텁은 동시에 실제 Kafka 전송(kafkaTemplate.send(...).get(5, SECONDS))을
		// 대신 막아, 이 테스트가 브로커 없이도(예: Kafka 가 안 떠 있는 환경) 실행되게 한다.
		TransactionTemplate requiresNewRead = new TransactionTemplate(transactionManager);
		requiresNewRead.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		AtomicBoolean rowStillVisibleFromOutsideAtPublishTime = new AtomicBoolean(false);
		doAnswer(invocation -> {
			boolean stillVisible = requiresNewRead.execute(status -> !repository.findBySid("SID-B").isEmpty());
			rowStillVisibleFromOutsideAtPublishTime.set(stillVisible);
			return null;
		}).when(publisher).publish(anyString(), any());

		sessionService.consumeForLogout("SID-B");

		verify(publisher).publish("SID-B", "user-sub-0001");
		// publish 가 불리는 순간 DB 밖에서는 아직 세션 행이 살아 있다는 사실 자체를 고정한다. 이 위치라면
		// 커밋이 실패했을 때 이미 나간 이벤트를 되돌릴 방법이 없다 — 세션은 살아 있는데 그 사용자의
		// refresh 만 죽는다. publish 가 커밋 뒤로(밖에서도 이미 행이 지워진 뒤로) 옮겨지면 이 단언이
		// 깨진다 — Task 7 이 outbox 로 바꿀 때 뒤집힐 지점이다.
		assertThat(rowStillVisibleFromOutsideAtPublishTime).isTrue();
	}

}
