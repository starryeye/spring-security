package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.dto.TokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Service
public class ClientCredentialsGrantService {

	/**
	 * client_credentials grant 를 처리한다. (RFC 6749 4.4)
	 *      사용자가 없는 grant 라 client 가 자기 자신으로서 토큰을 받는다.
	 *
	 * 주의. 이 grant 는 client_scopes 만 본다. scopes(사용자 위임) 는 쳐다보지 않는다 — 사용자가 없는데
	 *      "사용자가 위임한 권한" 을 줄 수는 없다. 그래서 openid 같은 scope 는 이 경로로 나올 수 없고,
	 *      그 결과 이 토큰으로 /userinfo 를 부르면 403 insufficient_scope 가 된다. 별도 방어 코드가 필요 없다.
	 *
	 * 주의. refresh token 을 발급하지 않는다(RFC 6749 4.4.3 이 SHOULD NOT). 사용자가 없으므로
	 *      "재로그인 없이 연장" 이라는 refresh 의 존재 이유가 없고, 필요하면 자격증명으로 다시 받으면 된다.
	 *      id token 도 없다 — 인증한 사용자가 없다.
	 *
	 * 주의. sub 는 client_id 다(RFC 9068). aud 도 client_id 가 되어 sub == aud 인데, 이 서버가
	 *      resource indicator(RFC 8707)를 쓰지 않아 발급 대상 자원을 표현할 방법이 없기 때문이다.
	 */

	private final AccessTokenIssuer accessTokenIssuer;
	private final long accessTokenTtlSeconds;

	public ClientCredentialsGrantService(
			AccessTokenIssuer accessTokenIssuer,
			@Value("${my.access-token-ttl-seconds}") long accessTokenTtlSeconds
	) {
		this.accessTokenIssuer = accessTokenIssuer;
		this.accessTokenTtlSeconds = accessTokenTtlSeconds;
	}

	public GrantResult grant(ClientInfo client, String requestedScope) {

		if (!client.grantTypes().contains("client_credentials")) {
			return GrantResult.failed("unauthorized_client", "client not authorized for client_credentials grant");
		}

		List<String> allowed = client.clientScopes();
		String effectiveScope = StringUtils.hasText(requestedScope)
				? String.join(" ", requestedScope.trim().split("\\s+"))
				: String.join(" ", allowed);

		if (!StringUtils.hasText(effectiveScope)) {
			return GrantResult.failed("invalid_scope", "client has no client scopes");
		}
		if (!allowed.containsAll(Arrays.asList(effectiveScope.split(" ")))) {
			return GrantResult.failed("invalid_scope", "requested scope exceeds the client scopes");
		}

		String accessToken = accessTokenIssuer.issue(client.clientId(), client.clientId(), effectiveScope);

		return GrantResult.ok(new TokenResponse(accessToken, "Bearer", accessTokenTtlSeconds,
				effectiveScope, null, null));
	}
}
