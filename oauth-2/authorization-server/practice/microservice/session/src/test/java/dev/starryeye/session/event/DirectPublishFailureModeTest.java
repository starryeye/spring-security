package dev.starryeye.session.event;

import dev.starryeye.session.SessionService;
import dev.starryeye.session.jpa.OidcSessionEntityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
	@DisplayName("[현재 동작] 발행은 트랜잭션 커밋 전에 일어난다 — 커밋이 실패하면 유령 이벤트가 남는다")
	void publishHappensBeforeCommit() {
		sessionService.register("SID-B", "user-sub-0001", "my-client");

		sessionService.consumeForLogout("SID-B");

		// publish 가 트랜잭션 안에서 호출된다는 사실 자체를 고정한다. 이 위치라면 커밋이 실패했을 때
		// 이미 나간 이벤트를 되돌릴 방법이 없다 — 세션은 살아 있는데 그 사용자의 refresh 만 죽는다.
		verify(publisher).publish("SID-B", "user-sub-0001");
	}

}
