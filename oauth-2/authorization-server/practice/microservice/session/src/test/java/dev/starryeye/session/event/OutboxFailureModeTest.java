package dev.starryeye.session.event;

import dev.starryeye.session.SessionService;
import dev.starryeye.session.jpa.OidcSessionEntityRepository;
import dev.starryeye.session.jpa.OutboxEntity;
import dev.starryeye.session.jpa.OutboxEntityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "my.outbox-poll-interval-ms=3600000")
class OutboxFailureModeTest {

	/**
	 * Task 6 이 고정했던 두 실패가 outbox 로 사라졌음을 같은 상황에서 확인한다.
	 *
	 * 주의. 이 클래스는 DirectPublishFailureModeTest 를 대체한 것이다. 커밋 diff 에서 두 파일을 나란히
	 *      보면 무엇이 달라졌는지가 드러난다 — 같은 상황, 반대 결과다.
	 */

	@Autowired
	private SessionService sessionService;

	@Autowired
	private OidcSessionEntityRepository sessionRepository;

	@Autowired
	private OutboxEntityRepository outboxRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Test
	@DisplayName("Kafka 가 죽어 있어도 로그아웃은 커밋된다 — 편지는 outbox 에 남는다")
	void logoutCommitsEvenWhenKafkaIsDown() {
		// Kafka 를 목으로 죽일 필요가 없다. 이 컨텍스트에는 브로커가 없고, 발행은 폴러가 별도로 하므로
		// consumeForLogout 은 Kafka 를 전혀 부르지 않는다 — 그것이 outbox 의 요점이다.
		sessionService.register("SID-A", "user-sub-0001", "my-client");

		sessionService.consumeForLogout("SID-A");

		assertThat(sessionRepository.findBySid("SID-A")).isEmpty();

		List<OutboxEntity> pending = outboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc();
		assertThat(pending).hasSize(1);
		assertThat(pending.get(0).getPartitionKey()).isEqualTo("SID-A");
		assertThat(pending.get(0).getPublishedAt()).isNull();
	}

	@Test
	@DisplayName("로그아웃이 롤백되면 편지도 함께 사라진다 — 유령 이벤트가 불가능하다")
	void rollbackTakesTheEventWithIt() {
		sessionService.register("SID-B", "user-sub-0001", "my-client");
		long before = outboxRepository.count();

		// 커밋 실패를 주입하는 데 테스트 전용 서비스 메서드(consumeForLogoutThenFail)를 두지 않았다.
		// 대신 테스트 쪽에서 TransactionTemplate 으로 트랜잭션을 직접 열고 그 안에서 consumeForLogout 을
		// 부른다. consumeForLogout 의 @Transactional 은 propagation 기본값(REQUIRED)이라 이미 열려 있는
		// 이 트랜잭션에 참여할 뿐 새로 열지 않는다 — 그래서 세션 삭제와 outbox INSERT 가 정말로 같은
		// 물리 트랜잭션 안에 들어간다. 콜백이 정상 반환된 뒤 status.setRollbackOnly() 가 서 있으면
		// TransactionTemplate 은 commit 대신 rollback 을 호출한다(AbstractPlatformTransactionManager
		// .commit 이 isRollbackOnly 를 먼저 확인한다) — 실제 커밋 실패와 관찰 가능한 결과가 같다.
		// 프록시를 우회하지 않으므로 트랜잭션 자체가 안 걸리는 문제도 없고, 운영 코드에 테스트만을 위한
		// 메서드도 남지 않는다.
		TransactionTemplate forcedRollback = new TransactionTemplate(transactionManager);
		forcedRollback.execute(status -> {
			sessionService.consumeForLogout("SID-B");
			status.setRollbackOnly();
			return null;
		});

		assertThat(sessionRepository.findBySid("SID-B")).hasSize(1); // 삭제가 롤백됐다
		assertThat(outboxRepository.count()).isEqualTo(before);      // 편지도 롤백됐다
	}
}
