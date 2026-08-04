package dev.starryeye.session;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LogoutTokenFactoryTest {

	private static final String BACKCHANNEL_LOGOUT_EVENT =
			"http://schemas.openid.net/event/backchannel-logout";

	private final LogoutTokenFactory factory = new LogoutTokenFactory("http://localhost:9000");

	private Map<String, Object> create() {
		return factory.create("SID-ABC", "user-sub-0001", "demo-rp");
	}

	// RP 는 자기가 discovery 로 알아낸 issuer 와 문자열 정확 일치로 대조한다.
	@Test
	void issuerMatchesTheAdvertisedIssuer() {
		assertThat(create()).containsEntry("iss", "http://localhost:9000");
	}

	// aud 에 그 RP 의 client_id 가 없으면 거부된다.
	@Test
	void audienceIsTheTargetClient() {
		assertThat(create()).containsEntry("aud", "demo-rp");
	}

	@Test
	void subjectAndSessionAreBothPresent() {
		assertThat(create()).containsEntry("sub", "user-sub-0001").containsEntry("sid", "SID-ABC");
	}

	@Test
	void issuedAtIsPresent() {
		assertThat(create()).containsKey("iat");
	}

	// jti 는 RP 가 재생을 판정하는 근거다. 두 번 만들면 서로 달라야 한다.
	@Test
	void jtiIsPresentAndUnique() {
		assertThat(create().get("jti")).isNotNull().isNotEqualTo(create().get("jti"));
	}

	// events 에 back-channel logout 키가 없으면 RP 는 이것을 로그아웃 사건으로 인정하지 않는다.
	@Test
	@SuppressWarnings("unchecked")
	void eventsContainsTheBackchannelLogoutKey() {
		Map<String, Object> events = (Map<String, Object>) create().get("events");

		assertThat(events).containsKey(BACKCHANNEL_LOGOUT_EVENT);
	}

	// nonce 가 있으면 RP 가 거부한다. logout token 이 id token 검증 경로로 흘러들어가는 것을 막는 규칙이다.
	@Test
	void nonceIsAbsent() {
		assertThat(create()).doesNotContainKey("nonce");
	}

	// exp 를 싣지 않는다. 검증기가 요구하지 않으며, exp 없는 JWT 는 access token 검증도 통과하지 못한다.
	@Test
	void expirationIsAbsent() {
		assertThat(create()).doesNotContainKey("exp");
	}
}
