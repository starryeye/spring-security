package dev.starryeye.token_state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.token_state.jpa.OutboxEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RefreshTokenServiceRevokeBySidTest {

	/**
	 * 로그아웃은 그 세션에 속한 refresh token 만 죽인다. 같은 사용자가 다른 브라우저에서 만든 세션은 살아야 한다.
	 *
	 * 주의. ACTIVE 인 행만 골라 폐기하므로 두 번 실행해도 결과가 같다 — 두 번째는 바꿀 행이 없어 사건도 쓰지 않는다.
	 *      Kafka 가 at-least-once 이므로 같은 이벤트를 두 번 받는 일이 실제로 일어나는데, 이 성질 덕에 별도 dedupe
	 *      표가 필요 없다.
	 *
	 * 주의. 사건 단언은 언제나 이 테스트가 발급한 계열(familyId)로 걸러 읽는다. 이 모듈의 h2 는 다른 컨텍스트와
	 *      outbox 테이블을 공유한다(OutboxEvents 주석 참고).
	 */

	@Autowired
	private RefreshTokenService service;

	@Autowired
	private RefreshTokenEntityRepository repository;

	@Autowired
	private OutboxEntityRepository outboxRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private TokenGenerator tokenGenerator;

	/**
	 * @SpringBootTest 는 클래스 사이에 컨텍스트(와 H2 인메모리 DB)를 재사용하고, 이 클래스 안에서도 메서드마다
	 *      트랜잭션을 되돌리지 않는다. unknownSidChangesNothing 은 정의상 ACTIVE 인 sid=SID-A 행을 하나
	 *      남기고 끝나므로, JUnit5 의 기본 메서드 순서(소스 순서가 아니다)가 그 뒤에 revokesOnlyThatSession 을
	 *      돌리면 남은 행이 개수 단언을 오염시킨다. 이 모듈의 다른 @SpringBootTest 클래스들
	 *      (RefreshTokenServiceRevokeTest 등)이 전부 쓰는 관례를 그대로 따른다.
	 */
	@BeforeEach
	void clean() {
		repository.deleteAll();
	}

	@Test
	@DisplayName("그 세션의 refresh 만 폐기하고 다른 세션은 건드리지 않는다")
	void revokesOnlyThatSession() {
		IssueResult sessionA = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");
		IssueResult sessionB = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-B");

		int revoked = service.revokeBySid("SID-A");

		assertThat(revoked).isEqualTo(1);
		assertThat(statusOf(sessionA)).isEqualTo(RefreshTokenStatus.REVOKED);
		assertThat(statusOf(sessionB)).isEqualTo(RefreshTokenStatus.ACTIVE);
	}

	@Test
	@DisplayName("한 세션에 여러 client 가 붙어 있으면 전부 폐기한다")
	void revokesEveryClientOfThatSession() {
		IssueResult first = service.issue("client-one", "user-sub-0001", "openid offline_access", 1000L, "SID-A");
		IssueResult second = service.issue("client-two", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

		int revoked = service.revokeBySid("SID-A");

		assertThat(revoked).isEqualTo(2);
		assertThat(statusOf(first)).isEqualTo(RefreshTokenStatus.REVOKED);
		assertThat(statusOf(second)).isEqualTo(RefreshTokenStatus.REVOKED);
	}

	@Test
	@DisplayName("두 번 폐기해도 결과가 같다 — 두 번째는 아무 행도 바꾸지 않는다")
	void isIdempotent() {
		service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

		assertThat(service.revokeBySid("SID-A")).isEqualTo(1);
		assertThat(service.revokeBySid("SID-A")).isEqualTo(0);
	}

	@Test
	@DisplayName("모르는 sid 는 아무것도 바꾸지 않는다")
	void unknownSidChangesNothing() {
		service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

		assertThat(service.revokeBySid("SID-UNKNOWN")).isEqualTo(0);
	}

	@Test
	@DisplayName("로그아웃은 실제로 바뀐 계열마다 REVOKED(SESSION_LOGGED_OUT) 하나를 쓴다 — 한 세션의 두 client")
	void logoutRecordsOneRevokedEventPerFamily() throws Exception {
		IssueResult first = service.issue("client-one", "user-sub-0001", "openid offline_access", 1000L, "SID-A");
		IssueResult second = service.issue("client-two", "user-sub-0001", "openid offline_access", 1000L, "SID-A");
		IssueResult other = service.issue("client-one", "user-sub-0001", "openid offline_access", 1000L, "SID-B");

		assertThat(service.revokeBySid("SID-A")).isEqualTo(2);

		assertRevoked(first.familyId(), "client-one");
		assertRevoked(second.familyId(), "client-two");
		assertThat(OutboxEvents.kindsOf(outboxRepository, objectMapper, other.familyId())).containsExactly("ISSUED");
	}

	@Test
	@DisplayName("같은 로그아웃을 다시 처리하면 아무것도 바뀌지 않고 사건도 없다")
	void redeliveredLogoutRecordsNothing() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

		assertThat(service.revokeBySid("SID-A")).isEqualTo(1);
		assertThat(service.revokeBySid("SID-A")).isZero();

		assertThat(OutboxEvents.kindsOf(outboxRepository, objectMapper, issued.familyId())).containsExactly("ISSUED", "REVOKED");
	}

	@Test
	@DisplayName("로그아웃은 CONSUMED 행을 남기므로 그 뒤의 옛 토큰 재사용도 탐지되고 따로 기록된다")
	void logoutLeavesConsumedRowsSoALaterReplayIsStillDetected() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");
		service.rotate(issued.refreshToken(), "my-client", null);
		service.revokeBySid("SID-A");

		assertThat(service.rotate(issued.refreshToken(), "my-client", null).status()).isEqualTo(RotateStatus.REUSE_DETECTED);

		List<JsonNode> events = OutboxEvents.eventsOf(outboxRepository, objectMapper, issued.familyId());
		assertThat(events).extracting(e -> e.get("kind").asText()).containsExactly("ISSUED", "ROTATED", "REVOKED", "REVOKED");
		assertThat(events.get(2).get("reason").asText()).isEqualTo("SESSION_LOGGED_OUT");
		assertThat(events.get(2).get("activeTokensRevoked").asInt()).isEqualTo(1);
		assertThat(events.get(3).get("reason").asText()).isEqualTo("REUSE_DETECTED");
		assertThat(events.get(3).get("revokedTokens").asInt()).isEqualTo(1);
		assertThat(events.get(3).get("activeTokensRevoked").asInt()).isZero();
	}

	@Test
	@DisplayName("일부가 끊긴 계열을 client 가 폐기하면 바뀐 CONSUMED 행만 센다(Review Focus 4)")
	void revokeOfPartlyRevokedFamilyCountsOnlyChangedRows() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");
		service.rotate(issued.refreshToken(), "my-client", null);
		service.revokeBySid("SID-A");

		assertThat(service.revoke(issued.refreshToken(), "my-client")).isTrue();

		JsonNode last = OutboxEvents.eventsOf(outboxRepository, objectMapper, issued.familyId()).get(3);
		assertThat(last.get("reason").asText()).isEqualTo("CLIENT_REVOKED");
		assertThat(last.get("revokedTokens").asInt()).isEqualTo(1);
		assertThat(last.get("activeTokensRevoked").asInt()).isZero();
	}

	@Test
	@DisplayName("만료됐지만 상태가 ACTIVE 인 행도 activeTokensRevoked 에 들어간다 — 수는 상태 기준이다")
	void expiredButActiveRowCountsAsActive() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");
		RefreshTokenFixtures.expireIndividualToken(repository,
				repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken())).orElseThrow());

		assertThat(service.revokeBySid("SID-A")).isEqualTo(1);

		assertThat(OutboxEvents.eventsOf(outboxRepository, objectMapper, issued.familyId()).get(1)
				.get("activeTokensRevoked").asInt()).isEqualTo(1);
	}

	/**
	 * 계열의 사건이 [ISSUED, REVOKED] 이고, 그 REVOKED 가 이 계열 자신의 client 와 세션(SID-A)을 싣고 있는지 본다.
	 */
	private void assertRevoked(String familyId, String clientId) {
		List<JsonNode> events = OutboxEvents.eventsOf(outboxRepository, objectMapper, familyId);
		assertThat(events).extracting(e -> e.get("kind").asText()).containsExactly("ISSUED", "REVOKED");
		JsonNode revoked = events.get(1);
		assertThat(revoked.get("familyId").asText()).isEqualTo(familyId);
		assertThat(revoked.get("reason").asText()).isEqualTo("SESSION_LOGGED_OUT");
		assertThat(revoked.get("clientId").asText()).isEqualTo(clientId);
		assertThat(revoked.get("sid").asText()).isEqualTo("SID-A");
		assertThat(revoked.get("revokedTokens").asInt()).isEqualTo(1);
		assertThat(revoked.get("activeTokensRevoked").asInt()).isEqualTo(1);
	}

	private RefreshTokenStatus statusOf(IssueResult issued) {
		return repository.findByFamilyId(issued.familyId()).get(0).getStatus();
	}
}
