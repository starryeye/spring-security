package dev.starryeye.token_state.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.token_state.IssueResult;
import dev.starryeye.token_state.OutboxEvents;
import dev.starryeye.token_state.RefreshTokenService;
import dev.starryeye.token_state.jpa.OutboxEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 3, topics = SessionLoggedOutConsumer.LOGGED_OUT_TOPIC)
class SessionLoggedOutConsumerTest {

	/**
	 * 로그아웃 이벤트를 받으면 그 세션의 refresh token 이 폐기된다 — 이 슬라이스가 닫으려는 결손이다.
	 */

	@Autowired
	private RefreshTokenService service;

	@Autowired
	private RefreshTokenEntityRepository repository;

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private OutboxEntityRepository outboxRepository;

	@Test
	@DisplayName("로그아웃 이벤트를 받으면 그 세션의 refresh 가 폐기된다")
	void revokesOnEvent() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");
		IssueResult other = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-B");

		String payload = objectMapper.writeValueAsString(new SessionLoggedOutEvent(
				UUID.randomUUID().toString(), "SID-A", "user-sub-0001", Instant.now()));
		kafkaTemplate.send(SessionLoggedOutConsumer.LOGGED_OUT_TOPIC, "SID-A", payload).get(5, TimeUnit.SECONDS);

		await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
				assertThat(repository.findByFamilyId(issued.familyId()).get(0).getStatus())
						.isEqualTo(RefreshTokenStatus.REVOKED));

		assertThat(repository.findByFamilyId(other.familyId()).get(0).getStatus())
				.isEqualTo(RefreshTokenStatus.ACTIVE);
	}

	@Test
	@DisplayName("같은 로그아웃 이벤트가 두 번 와도 REVOKED 사건은 하나다")
	void redeliveredLogoutEventAddsNoEvent() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-R");
		IssueResult marker = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-M");
		String payload = objectMapper.writeValueAsString(new SessionLoggedOutEvent(
				UUID.randomUUID().toString(), "SID-R", "user-sub-0001", Instant.now()));

		kafkaTemplate.send(SessionLoggedOutConsumer.LOGGED_OUT_TOPIC, "SID-R", payload).get(5, TimeUnit.SECONDS);
		kafkaTemplate.send(SessionLoggedOutConsumer.LOGGED_OUT_TOPIC, "SID-R", payload).get(5, TimeUnit.SECONDS);
		// 같은 키 → 같은 파티션에서 앞의 둘 뒤에 처리된다. 표지가 끊기면 앞의 둘도 끝났다.
		String markerPayload = objectMapper.writeValueAsString(new SessionLoggedOutEvent(
				UUID.randomUUID().toString(), "SID-M", "user-sub-0001", Instant.now()));
		kafkaTemplate.send(SessionLoggedOutConsumer.LOGGED_OUT_TOPIC, "SID-R", markerPayload).get(5, TimeUnit.SECONDS);

		await().atMost(10, TimeUnit.SECONDS).untilAsserted(() ->
				assertThat(OutboxEvents.kindsOf(outboxRepository, objectMapper, marker.familyId())).contains("REVOKED"));
		assertThat(OutboxEvents.kindsOf(outboxRepository, objectMapper, issued.familyId())).containsExactly("ISSUED", "REVOKED");
	}
}
