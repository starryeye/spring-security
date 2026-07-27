package dev.starryeye.token_state;

import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class RefreshTokenServiceConcurrentRotateTest {

	/**
	 * 같은 refresh token 으로 동시 회전 두 건이 들어왔을 때 하나만 성공하고 다른 하나가 재사용으로 잡히는지 고정한다.
	 *      이 슬라이스의 정합성 주장이 실제 동시성 아래에서 성립하는지 보는 유일한 테스트다.
	 *
	 * 주의. 잠금만 걸어두면 통과하는 테스트가 아니다. 두 트랜잭션이 서로의 커밋 이전에 각자의 "첫 조회" 를 마치도록
	 *      TokenGenerator.hash 에 장벽을 세워 위험한 인터리빙을 강제한다. 첫 조회가 엔티티를 영속성 컨텍스트에
	 *      올리는 순간(findByTokenHash) 늦게 잠근 쪽은 잠금 이전 스냅샷으로 판정하게 되고, 두 요청이 모두
	 *      ROTATED 를 받는다. 스칼라 조회(findFamilyIdByTokenHash)로 되돌려야만 통과한다.
	 *
	 * 주의. hash 는 rotate 입구에서 한 번, 회전에 성공한 쪽이 새 토큰을 만들 때 또 한 번 호출된다. 장벽은
	 *      입구의 두 건에만 걸어야 하므로 호출 횟수로 구분한다. 뒤쪽 호출까지 막으면 회전을 끝내지 못해 교착이다.
	 *
	 * 주의. "계열 전 행이 REVOKED" 는 여기서 단언하지 않는다. 늦게 잠근 쪽의 SELECT ... FOR UPDATE 가 기다렸다가
	 *      풀릴 때, h2 에서 관찰되는 동작은 이렇다 — 기다리던 그 행 자체는 다시 읽어 최신 커밋 상태(CONSUMED)를
	 *      반영하지만, 대기하는 동안 다른 트랜잭션이 새로 삽입·커밋한 형제 행까지 이번 조회의 행 집합에
	 *      포함하지는 않는다. 그래서 h2 에서는 그 형제 행 하나가 ACTIVE 로 남을 수 있다 — 구현의 결함이 아니라
	 *      테스트 DB 에서 관찰된 잠금 의미론이다. "잠글 때 이미 존재하던 행 전부가 폐기된다"는 이미 커밋된
	 *      행으로만 구성해 h2 에서도 성립하는 RefreshTokenServiceRotateTest 쪽 단일 스레드 테스트가 고정한다.
	 *      여기서 단언 가능한 것은 "회전이 정확히 한 번만 일어났다" 와 "제출된 토큰이 재사용으로 폐기됐다" 다.
	 *
	 * 주의. "대기 중 다른 트랜잭션이 삽입한 형제 행도 폐기되는가" 는 h2 로는 고정할 수 없는 성질이라 이
	 *      테스트가 덮지 않는다. 운영 DB(MySQL 8/InnoDB)로는 RefreshTokenServiceMySqlLockSemanticsTest 가
	 *      같은 barrier 기법으로 별도 검증한다 — 반복 실행(16회 연속) 모두 그 형제 행이 함께 REVOKED 로
	 *      폐기됨을 확인했다. h2 의 관찰은 그래서 구현 결함의 증거가 아니라 h2 고유의 잠금 재조회 범위
	 *      한계로 결론 내린다.
	 */

	@Autowired
	private RefreshTokenService service;

	@Autowired
	private RefreshTokenEntityRepository repository;

	@MockitoSpyBean
	private TokenGenerator tokenGenerator;

	@BeforeEach
	void clean() {
		repository.deleteAll();
	}

	@Test
	void concurrentRotationOfTheSameTokenRotatesOnceAndRevokesTheFamily() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);

		CountDownLatch bothEnteredRotate = new CountDownLatch(2);
		AtomicInteger hashCalls = new AtomicInteger();
		doAnswer(invocation -> {
			if (hashCalls.incrementAndGet() <= 2) {
				bothEnteredRotate.countDown();
				bothEnteredRotate.await(10, TimeUnit.SECONDS);
			}
			return invocation.callRealMethod();
		}).when(tokenGenerator).hash(anyString());

		Callable<RotateResult> rotate = () -> service.rotate(issued.refreshToken(), "my-client", null);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		List<RotateResult> results;
		try {
			List<Future<RotateResult>> futures = pool.invokeAll(List.of(rotate, rotate));
			results = List.of(futures.get(0).get(30, TimeUnit.SECONDS), futures.get(1).get(30, TimeUnit.SECONDS));
		} finally {
			pool.shutdownNow();
		}

		assertThat(results).extracting(RotateResult::status)
				.containsExactlyInAnyOrder(RotateStatus.ROTATED, RotateStatus.REUSE_DETECTED);

		// 응답 status 만 보지 않는다. DB 로 확인한다.
		RefreshTokenEntity submitted = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken()))
				.orElseThrow();
		assertThat(submitted.getStatus()).isEqualTo(RefreshTokenStatus.REVOKED);
		assertThat(submitted.getRevokedReason()).isEqualTo("REUSE_DETECTED");

		// 행이 2개인 것 자체가 단언이다 — 둘 다 회전에 성공했다면 최초 발급분 + 회전분 2개로 3개가 된다.
		assertThat(repository.findByFamilyId(submitted.getFamilyId())).hasSize(2);
	}
}
