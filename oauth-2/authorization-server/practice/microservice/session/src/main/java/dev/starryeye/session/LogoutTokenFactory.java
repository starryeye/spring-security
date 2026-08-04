package dev.starryeye.session;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class LogoutTokenFactory {

	/**
	 * logout token 의 claim 을 구성한다. (OIDC Back-Channel Logout 1.0 2.4)
	 *      서명은 signing 이 하고, 이 클래스는 claim 계약만 책임진다.
	 *
	 * 주의. nonce 를 싣지 않는다. 스펙이 금지하며 RP 가 거부한다. nonce 가 있으면 이 토큰이 id token 검증
	 *      경로에서 통과할 여지가 생겨 두 토큰 타입이 서로 통하게 된다.
	 *
	 * 주의. exp 를 싣지 않는다. 검증기가 요구하지 않고, exp 없는 JWT 는 access token 검증도 통과하지 못한다.
	 *
	 * 주의. iss 는 RP 가 discovery 로 알아낸 issuer 와 문자열 정확 일치로 대조한다. 포트 하나만 어긋나도
	 *      logout token 이 통째로 거부된다.
	 */

	static final String BACKCHANNEL_LOGOUT_EVENT = "http://schemas.openid.net/event/backchannel-logout";
	static final String LOGOUT_TOKEN_TYP = "logout+jwt";

	private final String issuer;

	public LogoutTokenFactory(@Value("${my.issuer}") String issuer) {
		this.issuer = issuer;
	}

	public Map<String, Object> create(String sid, String sub, String clientId) {
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("iss", issuer);
		claims.put("sub", sub);
		claims.put("aud", clientId);
		claims.put("iat", Instant.now().getEpochSecond());
		claims.put("jti", UUID.randomUUID().toString());
		claims.put("sid", sid);
		claims.put("events", Map.of(BACKCHANNEL_LOGOUT_EVENT, Map.of()));
		return claims;
	}
}
