package dev.starryeye.token_state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.token_state.jpa.OutboxEntity;
import dev.starryeye.token_state.jpa.OutboxEntityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RefreshTokenServiceEventTest {

	/**
	 * refresh token 계열의 사건이 outbox 에 어떤 모양으로 쓰이는지 본다. Kafka 로 나가는 부분은
	 *      OutboxPublisherTest 가 따로 본다 — 이 클래스는 DB 의 outbox 행만 읽는다.
	 *
	 * 주의. 단언은 언제나 이 테스트가 발급한 계열(familyId)로 걸러 읽는다. 이 모듈의 h2 는 다른 컨텍스트와
	 *      테이블을 공유한다(OutboxEvents 주석 참고).
	 */

	@Autowired
	private RefreshTokenService service;

	@Autowired
	private OutboxEntityRepository outboxRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private TokenGenerator tokenGenerator;

	@Test
	@DisplayName("발급하면 같은 트랜잭션에서 ISSUED 사건을 outbox 에 쓴다")
	void issueRecordsIssuedEvent() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

		List<OutboxEntity> rows = OutboxEvents.rowsOf(outboxRepository, issued.familyId());
		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).getTopic()).isEqualTo("oidc.refresh-token.lifecycle.v1");
		assertThat(rows.get(0).getPartitionKey()).isEqualTo(issued.familyId());

		JsonNode event = objectMapper.readTree(rows.get(0).getPayload());
		assertThat(event.get("eventId").asText()).isEqualTo(rows.get(0).getEventId());
		assertThat(event.get("kind").asText()).isEqualTo("ISSUED");
		assertThat(event.get("familyId").asText()).isEqualTo(issued.familyId());
		assertThat(event.get("sub").asText()).isEqualTo("user-sub-0001");
		assertThat(event.get("clientId").asText()).isEqualTo("my-client");
		assertThat(event.get("sid").asText()).isEqualTo("SID-A");
		Instant occurredAt = Instant.parse(event.get("occurredAt").asText());
		Instant expiresAt = Instant.parse(event.get("expiresAt").asText());
		assertThat(expiresAt.getEpochSecond()).isEqualTo(issued.expiresAt());
		assertThat(Duration.between(occurredAt, expiresAt)).isEqualTo(Duration.ofSeconds(60));          // 테스트 yml ttl
		assertThat(Duration.between(occurredAt, Instant.parse(event.get("familyExpiresAt").asText())))
				.isEqualTo(Duration.ofSeconds(300));                                                     // 테스트 yml 계열 상한
		assertThat(event.has("reason")).isFalse();
		assertThat(event.has("revokedTokens")).isFalse();
		assertThat(event.has("activeTokensRevoked")).isFalse();
		assertThat(rows.get(0).getPayload())
				.doesNotContain(issued.refreshToken())
				.doesNotContain(tokenGenerator.hash(issued.refreshToken()));
	}

	@Test
	@DisplayName("sid 가 없는 발급은 sid 키 없이 나간다")
	void issuedEventOmitsAbsentSid() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, null);

		assertThat(OutboxEvents.eventsOf(outboxRepository, objectMapper, issued.familyId()).get(0).has("sid")).isFalse();
	}
}
