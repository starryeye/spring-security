package dev.starryeye.token_state.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.token_state.IssueResult;
import dev.starryeye.token_state.RefreshTokenService;
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
}
