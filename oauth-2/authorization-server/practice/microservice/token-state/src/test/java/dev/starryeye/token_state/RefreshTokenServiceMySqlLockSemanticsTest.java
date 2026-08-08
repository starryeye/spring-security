package dev.starryeye.token_state;

import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
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

/**
 * design 문서(2026-07-25-microservice-token-lifecycle-slice3-design.md) §8 과
 *      RefreshTokenServiceConcurrentRotateTest 가 "미검증" 으로 남긴 성질을 실제 MySQL(InnoDB) 로 검증한다:
 *      계열 잠금을 먼저 얻은 트랜잭션이 새 행(형제 행 C)을 삽입·커밋하는 동안, 잠금을 기다리던 다른
 *      트랜잭션이 잠금을 얻은 뒤의 SELECT ... FOR UPDATE 결과 집합에 그 형제 행이 포함되는가.
 *
 * 주의. h2 는 InnoDB 의 next-key/gap lock 의미론을 재현하지 않으므로 이 성질은 h2 로 고정할 수 없다
 *      (RefreshTokenServiceConcurrentRotateTest 의 관찰 참고). 그래서 이 클래스만 별도 프로필
 *      (application-mysql-verify.yml)로 MySQL 에 접속해 확인한다 — 운영 스키마(microservice_as)가 아니라
 *      격리된 token_state_test 스키마를 쓴다.
 *
 * 주의. MySQL 이 없는 환경(로컬 개발 · CI)에서는 이 클래스 전체를 건너뛴다. {@link #requireMySql()} 의
 *      assumeTrue 는 JUnit 5 lifecycle 상 Spring 의 TestInstancePostProcessor(컨텍스트 로딩)보다 먼저
 *      실행되는 정적 @BeforeAll 안에 있으므로, MySQL 이 없을 때 컨텍스트 로딩 실패로 에러가 나는 대신
 *      깨끗하게 skip 된다.
 */
@SpringBootTest
@ActiveProfiles("mysql-verify")
class RefreshTokenServiceMySqlLockSemanticsTest {

	private static final String MYSQL_HOST = "localhost";
	private static final int MYSQL_PORT = 3306;

	@BeforeAll
	static void requireMySql() {
		String reason = "MySQL(" + MYSQL_HOST + ":" + MYSQL_PORT + ") 에 연결할 수 없어 InnoDB 잠금 의미론 검증을 건너뜁니다. "
				+ "docker compose -p microservice-as -f docker-compose/docker-compose.yml up -d mysql 로 띄운 뒤 재실행하세요.";
		boolean reachable = isReachable();
		if (!reachable) {
			// Assumptions 로만 남기면 Gradle 기본 test 로깅이 그 메시지를 콘솔에 보여주지 않는다.
			// SKIPPED 로만 뜨고 "왜" 가 사라지므로, 표준 출력에도 같은 사유를 직접 남긴다.
			System.out.println("[SKIP] " + RefreshTokenServiceMySqlLockSemanticsTest.class.getSimpleName() + ": " + reason);
		}
		Assumptions.assumeTrue(reachable, reason);
	}

	private static boolean isReachable() {
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress(MYSQL_HOST, MYSQL_PORT), 500);
			return true;
		} catch (IOException e) {
			return false;
		}
	}

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

	/**
	 * RefreshTokenServiceConcurrentRotateTest 와 같은 barrier 기법으로 같은 인터리빙을 강제한다 —
	 *      두 트랜잭션이 서로의 커밋 전에 각자의 스칼라 첫 조회(findFamilyIdByTokenHash)를 마치도록
	 *      TokenGenerator.hash 에 장벽을 세워, 실제로 findByFamilyIdForUpdate 잠금 경쟁이 붙게 한다.
	 *      h2 에서는 이 결과 중 "회전이 정확히 한 번만 일어났다" 와 "제출된 토큰이 재사용으로 폐기됐다" 만
	 *      단언했다. 여기서는 승자(ROTATED)가 만든 새 행(형제 행 C)의 최종 상태까지 MySQL 로 확인한다.
	 */
	@Test
	void siblingRowInsertedWhileWaitingForFamilyLockIsIncludedInRevocation() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L, null);

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

		RotateResult rotated = results.stream()
				.filter(r -> r.status() == RotateStatus.ROTATED)
				.findFirst()
				.orElseThrow();

		RefreshTokenEntity submitted = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken()))
				.orElseThrow();
		assertThat(submitted.getStatus()).isEqualTo(RefreshTokenStatus.REVOKED);
		assertThat(submitted.getRevokedReason()).isEqualTo("REUSE_DETECTED");

		// 계열 행 개수 자체가 단언이다 — 둘 다 회전에 성공했다면 3개가 된다 (기존 h2 테스트와 동일한 관용구).
		assertThat(repository.findByFamilyId(submitted.getFamilyId())).hasSize(2);

		// 검증 대상 성질: 잠금을 먼저 얻은 트랜잭션이 삽입·커밋한 형제 행(C) — 잠금을 기다리던 트랜잭션이
		// 대기 후 다시 읽은 잠긴 목록에 이 행을 포함해 REUSE_DETECTED 폐기 대상에 넣었는가.
		RefreshTokenEntity sibling = repository.findByTokenHash(tokenGenerator.hash(rotated.refreshToken()))
				.orElseThrow();
		assertThat(sibling.getStatus())
				.as("대기 중 삽입된 형제 행이 InnoDB SELECT ... FOR UPDATE 재조회에 포함돼 폐기됐는가 (family_id=%s)",
						submitted.getFamilyId())
				.isEqualTo(RefreshTokenStatus.REVOKED);
	}
}
