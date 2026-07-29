package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.dto.TokenResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Service
public class ClientCredentialsGrantService {

	/**
	 * client_credentials grant 를 처리한다. (RFC 6749 4.4)
	 *      사용자가 없는 grant 라 client 가 자기 자신으로서 토큰을 받는다.
	 *
	 * 주의. 이 grant 는 client_scopes 만 본다. scopes(사용자 위임) 는 쳐다보지 않는다 — 사용자가 없는데
	 *      "사용자가 위임한 권한" 을 줄 수는 없다. 다만 두 컬럼은 스키마상 분리돼 있을 뿐, 무엇을 담을지에
	 *      대한 제약은 없다 — client_scopes 에 openid 를 넣는 것을 막는 것이 아무 것도 없다. 그런 client 가
	 *      이 grant 로 openid scope 토큰을 받으면 /userinfo 의 openid 검사는 통과하고, needsProfileLookup 이
	 *      false 라 user-directory 조회조차 없이 200 {"sub": client_id} 가 나간다 — 사용자가 아닌 주체에 대한
	 *      userinfo 응답이다. grant() 의 사용자 위임 scope 가드(USER_DELEGATED_SCOPES)가 이를 막는다.
	 *      가드는 effectiveScope 에 적용되므로 요청 scope 든 client_scopes 전체를 쓰는 기본값이든 똑같이
	 *      걸린다 — client_scopes 자체가 잘못 설정돼 있으면 조용히 위험한 토큰을 내주는 대신 첫 사용에서
	 *      시끄럽게 실패하는 쪽을 택한다.
	 *
	 * 주의. refresh token 을 발급하지 않는다(RFC 6749 4.4.3 이 SHOULD NOT). 사용자가 없으므로
	 *      "재로그인 없이 연장" 이라는 refresh 의 존재 이유가 없고, 필요하면 자격증명으로 다시 받으면 된다.
	 *      id token 도 없다 — 인증한 사용자가 없다.
	 *
	 * 주의. sub 는 client_id 다(RFC 9068). aud 도 client_id 가 되어 sub == aud 인데, 이 서버가
	 *      resource indicator(RFC 8707)를 쓰지 않아 발급 대상 자원을 표현할 방법이 없기 때문이다.
	 */

	// 이 서버가 지원하는 OIDC scope 전부다(discovery 의 scopes_supported 중 introspect 를 제외한 나머지).
	// 전부 사용자 위임을 전제로 한다 — openid 는 최종 사용자 인증 요청의 표식(OIDC Core 3.1.2.1),
	// profile/email 은 사용자 claim, offline_access 는 사용자 동의가 전제인 refresh 발급 신호다.
	// 사용자가 없는 이 grant 에서는 셋 다 의미가 없다.
	private static final Set<String> USER_DELEGATED_SCOPES = Set.of("openid", "profile", "email", "offline_access");

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

		List<String> requested = Arrays.asList(effectiveScope.split(" "));

		// 요청 scope 든 client_scopes 전체를 쓴 기본값이든 동일하게 적용된다 (effectiveScope 기준).
		if (requested.stream().anyMatch(USER_DELEGATED_SCOPES::contains)) {
			return GrantResult.failed("invalid_scope", "user-delegated scope is not available from client_credentials");
		}
		if (!allowed.containsAll(requested)) {
			return GrantResult.failed("invalid_scope", "requested scope exceeds the client scopes");
		}

		String accessToken = accessTokenIssuer.issue(client.clientId(), client.clientId(), effectiveScope);

		return GrantResult.ok(new TokenResponse(accessToken, "Bearer", accessTokenTtlSeconds,
				effectiveScope, null, null));
	}
}
