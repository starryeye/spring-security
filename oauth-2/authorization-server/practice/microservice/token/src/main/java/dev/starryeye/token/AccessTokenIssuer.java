package dev.starryeye.token;

import dev.starryeye.token.client.SigningClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AccessTokenIssuer {

	/**
	 * access token 의 claim 을 구성해 signing 에 서명을 위임한다.
	 *      authorization_code 와 refresh_token 두 grant 가 같은 claim 집합을 내야 하므로 한 곳에 둔다.
	 *
	 * 주의. scope claim 을 JSON 배열로 낸다. RFC 9068 은 공백 구분 문자열을 요구하지만 이 서버는 슬라이스 1부터
	 *      배열을 써 왔고 AccessTokenVerifier 도 배열로 읽는다. 형식을 바꾸려면 양쪽을 함께 바꿔야 한다.
	 */

	private final SigningClient signingClient;

	@Value("${my.issuer}")
	private String issuer;

	@Value("${my.access-token-ttl-seconds}")
	private long accessTokenTtlSeconds;

	public String issue(String sub, String clientId, String scope) {
		Instant now = Instant.now();
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("iss", issuer);
		claims.put("sub", sub);
		claims.put("aud", clientId);
		claims.put("iat", now.getEpochSecond());
		claims.put("exp", now.plusSeconds(accessTokenTtlSeconds).getEpochSecond());
		claims.put("scope", Arrays.asList(scope.split(" ")));
		return signingClient.sign(claims);
	}
}
