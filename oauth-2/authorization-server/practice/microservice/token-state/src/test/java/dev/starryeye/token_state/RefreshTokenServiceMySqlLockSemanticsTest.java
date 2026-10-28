package dev.starryeye.token_state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.token_state.event.RefreshTokenEventPublisher;
import dev.starryeye.token_state.jpa.OutboxEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * design 문서(2026-07-25-microservice-token-lifecycle-slice3-design.md) §8 과
 *      RefreshTokenServiceConcurrentRotateTest 가 "미검증" 으로 남긴 성질을 실제 MySQL(InnoDB) 로 검증한다:
 *      계열 잠금을 먼저 얻은 트랜잭션이 새 행(형제 행 C)을 삽입·커밋하는 동안, 잠금을 기다리던 다른
 *      트랜잭션이 잠금을 얻은 뒤의 SELECT ... FOR UPDATE 결과 집합에 그 형제 행이 포함되는가.
 *
 * 나머지 세 테스트는 한 계열에 두 트랜잭션이 겹치는 순서를 하나씩 정해 놓고 결과를 본다. 먼저 잠근 쪽을 잠금을 쥔
 *      채 세워 두고, 다른 쪽이 실제로 잠금을 기다리는 것을 MySQL 에 물어(awaitLockWait) 확인한 뒤 풀어 준다.
 *      (1) 회전 뒤에 잠금을 얻은 재사용은 회전보다 늦은 시각으로 기록되고 새 행까지 계열 전체를 끊는다.
 *      (2) 회전이 잠금을 쥔 동안 기다린 로그아웃(revokeBySid)은 회전이 삽입한 새 행을 보고 그 행을 끊는다.
 *      (3) 로그아웃이 잠금을 쥔 동안 기다린 회전은 끊긴 계열을 보고 REVOKED 로 거절되며 사건을 쓰지 않는다.
 *      두 Future 가 모두 값을 돌려주는 것이 그 순서에서 교착이 없다는 단언이다 — 교착이 나면 어느 한쪽의
 *      Future.get 이 값 대신 예외(또는 시간 초과)로 끝난다. 한 로그아웃이 여러 계열을 잠그는 동안의 경합과
 *      인접한 계열 사이의 gap lock 상호작용은 이 클래스가 보이지 않는다.
 *
 * 주의. h2 는 InnoDB 의 next-key/gap lock 의미론을 재현하지 않으므로 이 성질은 h2 로 고정할 수 없다
 *      (RefreshTokenServiceConcurrentRotateTest 의 관찰 참고). 그래서 이 클래스만 별도 프로필
 *      (application-mysql-verify.yml)로 MySQL 에 접속해 확인한다 — 운영 스키마(ms_token_state, 슬라이스
 *      7부터의 이름)가 아니라 격리된 token_state_test 스키마를 쓴다.
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

	@MockitoSpyBean
	private RefreshTokenEntityRepository repository;

	@MockitoSpyBean
	private TokenGenerator tokenGenerator;

	@MockitoSpyBean
	private RefreshTokenEventPublisher eventPublisher;

	@Autowired
	private OutboxEntityRepository outboxRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void clean() {
		repository.deleteAll();
		outboxRepository.deleteAll();
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

	/**
	 * 회전이 계열 잠금을 쥔 동안 같은 토큰의 재사용이 그 잠금을 기다리면, 재사용의 REVOKED 는 ROTATED 보다 늦은 시각으로
	 *      기록되고 회전이 만든 새 행까지 계열 전체를 끊는다(slice 9 design 문서 §3). RefreshTokenEventOrderTest 의
	 *      InnoDB 판이다 — 그쪽은 잠금 대기를 재현하지 않는다.
	 *
	 * 주의. "late" 는 회전보다 먼저 rotate 에 들어와 해시 · 스칼라 조회를 마치고, 계열 잠금 조회(findByFamilyIdForUpdate)
	 *      입구에서 선다. "first" 가 잠금을 얻고 generate 안에서 멈춘 뒤에야 late 가 잠금으로 가므로 실제로 기다린다.
	 *      시각을 rotate 입구에서 재거나 스칼라 조회 뒤 · 잠금 앞에서 재는 구현이면 late 의 시각이 first 보다 일러
	 *      시각 단언이 깨진다. late 를 스칼라 조회 안(실제 조회가 돌아온 뒤)에서 세우면 그다음의 시각 측정이 first 의
	 *      잠금 뒤로 밀려 "스칼라 조회 뒤 · 잠금 앞" 측정을 가려내지 못한다 — 그래서 잠금 조회 입구에서 세운다.
	 *
	 * 주의. 이 정지는 rotate 가 findByFamilyIdForUpdate 를 부르는 데 기댄다. 잠금 조회를 다른 메서드로 바꾼 구현에서는
	 *      late 가 서지 않아 순서 단언에 닿기 전에 lateEntered 대기에서 실패한다.
	 */
	@Test
	void reuseWaitingForTheFamilyLockIsRecordedAfterTheRotation() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1700000000L, "SID-A");

		CountDownLatch lateEntered = new CountDownLatch(1);
		CountDownLatch firstHoldsLock = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		doAnswer(invocation -> {
			if ("late".equals(Thread.currentThread().getName())) {
				lateEntered.countDown();
				awaitOrFail(firstHoldsLock, "firstHoldsLock");
			}
			return callRepository(invocation);
		}).when(repository).findByFamilyIdForUpdate(anyString());
		pauseFirstInsideGenerate(firstHoldsLock, releaseFirst);

		ExecutorService late = singleThread("late");
		ExecutorService first = singleThread("first");
		try {
			Future<RotateResult> reuse = late.submit(() -> service.rotate(issued.refreshToken(), "my-client", null));
			assertThat(lateEntered.await(10, TimeUnit.SECONDS)).isTrue();
			Future<RotateResult> rotation = first.submit(() -> service.rotate(issued.refreshToken(), "my-client", null));
			assertThat(firstHoldsLock.await(10, TimeUnit.SECONDS)).isTrue();
			awaitLockWait();
			releaseFirst.countDown();

			assertThat(rotation.get(30, TimeUnit.SECONDS).status()).isEqualTo(RotateStatus.ROTATED);
			assertThat(reuse.get(30, TimeUnit.SECONDS).status()).isEqualTo(RotateStatus.REUSE_DETECTED);
		} finally {
			stop(late);
			stop(first);
		}

		List<JsonNode> events = OutboxEvents.eventsOf(outboxRepository, objectMapper, issued.familyId());
		assertThat(events).extracting(e -> e.get("kind").asText()).containsExactly("ISSUED", "ROTATED", "REVOKED");
		assertThat(occurredAt(events.get(2))).isAfterOrEqualTo(occurredAt(events.get(1)));
		assertThat(events.get(2).get("reason").asText()).isEqualTo("REUSE_DETECTED");
		assertThat(events.get(2).get("revokedTokens").asInt()).isEqualTo(2);
		assertThat(events.get(2).get("activeTokensRevoked").asInt()).isEqualTo(1);
		assertThat(repository.findByFamilyId(issued.familyId())).hasSize(2)
				.allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(RefreshTokenStatus.REVOKED));
	}

	/**
	 * 회전이 계열 잠금을 쥔 동안 같은 세션의 로그아웃(revokeBySid)이 그 잠금을 기다리면, 깨어난 로그아웃은 회전이 삽입한
	 *      새 ACTIVE 행을 보고 그 행을 끊는다. 원래 행은 회전이 남긴 CONSUMED 그대로다. 로그아웃의 첫 조회는 계열 id
	 *      만 꺼내는 스칼라 조회라, 판정은 잠금 뒤 findByFamilyIdForUpdate 가 다시 읽은 상태로 한다.
	 *
	 * 주의. 로그아웃이 계열을 잠그지 않고 findByFamilyId 로 읽으면 이 테스트가 깨진다. 로그아웃은 회전 전 스냅샷의
	 *      원래 행만 보고 그 행을 폐기 대상으로 삼으며, 커밋 때의 UPDATE 가 회전의 행 잠금을 기다렸다가 CONSUMED 를
	 *      REVOKED 로 덮어쓴다. 새 행은 ACTIVE 로 남는다 — 로그아웃이 회전으로 생긴 토큰을 놓친다.
	 */
	@Test
	void logoutWaitingBehindARotationRevokesTheRotatedToken() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1700000000L, "SID-A");

		CountDownLatch firstHoldsLock = new CountDownLatch(1);
		CountDownLatch releaseFirst = new CountDownLatch(1);
		pauseFirstInsideGenerate(firstHoldsLock, releaseFirst);

		ExecutorService first = singleThread("first");
		ExecutorService logout = singleThread("logout");
		RotateResult rotated;
		try {
			Future<RotateResult> rotation = first.submit(() -> service.rotate(issued.refreshToken(), "my-client", null));
			assertThat(firstHoldsLock.await(10, TimeUnit.SECONDS)).isTrue();
			Future<Integer> revoked = logout.submit(() -> service.revokeBySid("SID-A"));
			awaitLockWait();
			releaseFirst.countDown();

			rotated = rotation.get(30, TimeUnit.SECONDS);
			assertThat(rotated.status()).isEqualTo(RotateStatus.ROTATED);
			assertThat(revoked.get(30, TimeUnit.SECONDS)).isEqualTo(1);
		} finally {
			stop(first);
			stop(logout);
		}

		RefreshTokenEntity original = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken())).orElseThrow();
		assertThat(original.getStatus()).isEqualTo(RefreshTokenStatus.CONSUMED);
		RefreshTokenEntity sibling = repository.findByTokenHash(tokenGenerator.hash(rotated.refreshToken())).orElseThrow();
		assertThat(sibling.getStatus()).isEqualTo(RefreshTokenStatus.REVOKED);
		assertThat(sibling.getRevokedReason()).isEqualTo("SESSION_LOGGED_OUT");

		List<JsonNode> events = OutboxEvents.eventsOf(outboxRepository, objectMapper, issued.familyId());
		assertThat(events).extracting(e -> e.get("kind").asText()).containsExactly("ISSUED", "ROTATED", "REVOKED");
		assertThat(events.get(2).get("reason").asText()).isEqualTo("SESSION_LOGGED_OUT");
		assertThat(events.get(2).get("revokedTokens").asInt()).isEqualTo(1);
		assertThat(events.get(2).get("activeTokensRevoked").asInt()).isEqualTo(1);
		assertThat(occurredAt(events.get(2))).isAfterOrEqualTo(occurredAt(events.get(1)));
	}

	/**
	 * 로그아웃이 계열 잠금을 쥔 동안 같은 계열의 회전이 그 잠금을 기다리면, 깨어난 회전은 끊긴 계열을 보고 REVOKED 로
	 *      거절된다. 새 행도 ROTATED 사건도 생기지 않는다. 로그아웃은 잠금을 쥔 채 REVOKED 사건을 쓰려는 자리
	 *      (eventPublisher.revoked)에서 선다.
	 *
	 * 주의. 로그아웃이 계열을 잠그지 않으면 회전이 기다릴 잠금이 없어 awaitLockWait 에서 깨진다. 회전이 계열을 잠그지
	 *      않고 findByFamilyId 로 읽으면, 회전은 기다린 뒤에도 로그아웃 전 스냅샷의 ACTIVE 로 판정해 ROTATED 를
	 *      돌려주므로 회전 결과 단언에서 깨진다.
	 */
	@Test
	void rotationWaitingBehindALogoutFindsTheFamilyRevoked() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1700000000L, "SID-A");

		CountDownLatch logoutHoldsLock = new CountDownLatch(1);
		CountDownLatch releaseLogout = new CountDownLatch(1);
		doAnswer(invocation -> {
			if ("logout".equals(Thread.currentThread().getName())) {
				logoutHoldsLock.countDown();
				awaitOrFail(releaseLogout, "releaseLogout");
			}
			return invocation.callRealMethod();
		}).when(eventPublisher).revoked(any(), any(), anyString(), anyInt(), anyInt());

		ExecutorService logout = singleThread("logout");
		ExecutorService first = singleThread("first");
		try {
			Future<Integer> revoked = logout.submit(() -> service.revokeBySid("SID-A"));
			assertThat(logoutHoldsLock.await(10, TimeUnit.SECONDS)).isTrue();
			Future<RotateResult> rotation = first.submit(() -> service.rotate(issued.refreshToken(), "my-client", null));
			awaitLockWait();
			releaseLogout.countDown();

			assertThat(revoked.get(30, TimeUnit.SECONDS)).isEqualTo(1);
			assertThat(rotation.get(30, TimeUnit.SECONDS).status()).isEqualTo(RotateStatus.REVOKED);
		} finally {
			stop(logout);
			stop(first);
		}

		List<RefreshTokenEntity> family = repository.findByFamilyId(issued.familyId());
		assertThat(family).hasSize(1);
		assertThat(family.get(0).getStatus()).isEqualTo(RefreshTokenStatus.REVOKED);
		assertThat(family.get(0).getRevokedReason()).isEqualTo("SESSION_LOGGED_OUT");
		assertThat(OutboxEvents.kindsOf(outboxRepository, objectMapper, issued.familyId()))
				.containsExactly("ISSUED", "REVOKED");
	}

	// "first" 스레드의 회전을 새 토큰 생성(generate) 안에서 세운다. generate 는 계열 잠금과 now 뒤에 불리므로,
	// 여기서 멈춘 회전은 잠금을 쥔 채 커밋하지 않은 상태다.
	private void pauseFirstInsideGenerate(CountDownLatch firstHoldsLock, CountDownLatch releaseFirst) {
		doAnswer(invocation -> {
			if ("first".equals(Thread.currentThread().getName())) {
				firstHoldsLock.countDown();
				awaitOrFail(releaseFirst, "releaseFirst");
			}
			return invocation.callRealMethod();
		}).when(tokenGenerator).generate();
	}

	// 다른 트랜잭션이 잠금을 기다리고 있는지 MySQL 에 직접 묻는다 — 시간에 맡기지 않는다.
	// 주의. 어느 잠금인지는 가리지 않는다. 계열 잠금을 쓰는 구현에서 기다리는 것은 계열 잠금
	//      (idx_refresh_tokens_family 의 X 잠금)이지만, 계열을 잠그지 않는 구현에서 커밋 때의 UPDATE 가 행 잠금
	//      (PRIMARY)을 기다려도 이 조건은 참이 된다.
	private void awaitLockWait() {
		await().atMost(10, TimeUnit.SECONDS).until(() -> jdbcTemplate.queryForObject(
				"select count(*) from performance_schema.data_locks"
						+ " where OBJECT_SCHEMA = 'token_state_test' and LOCK_STATUS = 'WAITING'",
				Integer.class) > 0);
	}

	// 기다리다 시간이 지나면 예외로 끝낸다. 조용히 넘어가면 세우려던 순서가 풀린 채 실제 경합으로 바뀐다.
	private static void awaitOrFail(CountDownLatch latch, String name) throws InterruptedException {
		if (!latch.await(10, TimeUnit.SECONDS)) {
			throw new IllegalStateException(name + " 를 10초 안에 받지 못했다");
		}
	}

	private static ExecutorService singleThread(String name) {
		return Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, name));
	}

	// 멈춘 스레드가 트랜잭션(과 잠금)을 쥔 채 남으면 다음 테스트의 clean() 이 그 잠금에 걸린다. 끝날 때까지 기다린다.
	private static void stop(ExecutorService executor) throws InterruptedException {
		executor.shutdownNow();
		if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
			throw new IllegalStateException("테스트 스레드가 10초 안에 끝나지 않았다");
		}
	}

	// 저장소 스파이는 Spring Data 저장소(JDK 프록시)를 감싼 인터페이스 mock 이라, invocation.callRealMethod() 는
	// "Cannot call abstract real method" 로 실패한다. 스파이의 기본 응답이 감싼 저장소로 호출을 넘기므로 그것으로
	// 실제 조회를 부른다.
	private Object callRepository(InvocationOnMock invocation) throws Throwable {
		return Mockito.mockingDetails(repository).getMockCreationSettings().getDefaultAnswer().answer(invocation);
	}

	private static Instant occurredAt(JsonNode event) {
		return Instant.parse(event.get("occurredAt").asText());
	}
}
