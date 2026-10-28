package dev.starryeye.token_state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.token_state.jpa.OutboxEntityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class RefreshTokenEventOrderTest {

	/**
	 * 회전과 재사용이 겹친 한 장면에서 사건의 occurredAt 순서를 본다. occurredAt 은 요청이 들어온 시각이 아니라
	 *      계열 잠금을 얻은 뒤에 잰 시각이라야 한다.
	 */

	@Autowired
	private RefreshTokenService service;

	@Autowired
	private OutboxEntityRepository outboxRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoSpyBean
	private TokenGenerator tokenGenerator;

	/**
	 * 시각은 계열 잠금을 얻은 뒤에 잰다(slice 9 design 문서 §3). 잠금을 기다린 재사용이 그 사이 커밋된 회전보다 이른
	 *      시각으로 기록되면, 감사 이력에서 "끊김"이 "회전"보다 먼저 일어난 것처럼 보인다.
	 *
	 * 주의. 잠금 대기를 그대로 재현하지 않는다. 늦게 처리될 쪽("late" 스레드)을 rotate 입구(첫 hash 호출)에서 세워
	 *      두고, 그동안 다른 스레드가 회전을 끝까지 커밋한 뒤 풀어 준다. 시각을 입구에서 재는 구현이면 late 의 시각이
	 *      회전보다 이르고, 잠금 뒤에 재는 구현이면 늦다 — 잠금 의미론과 무관하게 결정적이라 h2 로도 성립한다.
	 *      대신 InnoDB 에서 실제로 잠금을 기다리는 경우는 이 테스트가 보이지 못한다. 또 late 를 입구에서 세우므로, 시각을
	 *      스칼라 조회 뒤 · 잠금 앞에서 재는 구현도 가려내지 못한다 — late 는 풀린 뒤에 스칼라 조회를 하므로 그 시각도
	 *      회전보다 늦다. 이 두 가지는 RefreshTokenServiceMySqlLockSemanticsTest 의
	 *      reuseWaitingForTheFamilyLockIsRecordedAfterTheRotation 이 InnoDB 의 실제 잠금 대기로 같은 순서를 단언하며
	 *      보인다(MySQL 이 없으면 그 클래스는 건너뛴다).
	 */
	@Test
	@DisplayName("회전 뒤에 처리된 재사용은 회전보다 늦은 시각으로 기록된다")
	void reuseProcessedAfterARotationIsRecordedAfterIt() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1000L, "SID-A");

		CountDownLatch lateEntered = new CountDownLatch(1);
		CountDownLatch rotationCommitted = new CountDownLatch(1);
		AtomicBoolean lateFirstHash = new AtomicBoolean(true);
		doAnswer(invocation -> {
			if ("late".equals(Thread.currentThread().getName()) && lateFirstHash.getAndSet(false)) {
				lateEntered.countDown();
				// 시간이 지나 그냥 넘어가면 세우려던 순서가 풀린 채 실제 경합으로 바뀐다 — 예외로 끝낸다.
				if (!rotationCommitted.await(10, TimeUnit.SECONDS)) {
					throw new IllegalStateException("rotationCommitted 를 10초 안에 받지 못했다");
				}
			}
			return invocation.callRealMethod();
		}).when(tokenGenerator).hash(anyString());

		ExecutorService late = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "late"));
		try {
			Future<RotateResult> reuse = late.submit(() -> service.rotate(issued.refreshToken(), "my-client", null));
			assertThat(lateEntered.await(10, TimeUnit.SECONDS)).isTrue();

			RotateResult rotated = service.rotate(issued.refreshToken(), "my-client", null);
			rotationCommitted.countDown();

			assertThat(rotated.status()).isEqualTo(RotateStatus.ROTATED);
			assertThat(reuse.get(30, TimeUnit.SECONDS).status()).isEqualTo(RotateStatus.REUSE_DETECTED);
		} finally {
			late.shutdownNow();
			if (!late.awaitTermination(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("late 스레드가 10초 안에 끝나지 않았다");
			}
		}

		List<JsonNode> events = OutboxEvents.eventsOf(outboxRepository, objectMapper, issued.familyId());
		assertThat(events).extracting(e -> e.get("kind").asText()).containsExactly("ISSUED", "ROTATED", "REVOKED");
		assertThat(Instant.parse(events.get(2).get("occurredAt").asText()))
				.isAfterOrEqualTo(Instant.parse(events.get(1).get("occurredAt").asText()));
	}
}
