package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.RotateResult;
import dev.starryeye.token.client.TokenStateClient;
import dev.starryeye.token.dto.TokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class RefreshTokenGrantService {

	/**
	 * refresh grant 를 처리한다. 회전은 token-state 에 한 번의 호출로 위임하고, 결과로 새 access token 과
	 *      (openid 가 있으면) id token 을 조립한다.
	 *
	 * 주의. 회전 실패 사유를 전부 invalid_grant 하나로 뭉갠다. "이미 소진됐다" 와 "그런 토큰 없다" 를 구분해 주면
	 *      공격자가 토큰의 상태를 탐색할 수 있다. 구분은 로그에만 남긴다.
	 *
	 * 주의. scope 축소(RFC 6749 6)는 이번 access token 에만 적용된다. 저장된 refresh 의 scope 는 token-state 가
	 *      그대로 유지하므로, 한 번 좁혀도 다음 회전에서 원래 범위로 돌아온다.
	 */

	private final TokenStateClient tokenStateClient;
	private final AccessTokenIssuer accessTokenIssuer;
	private final IdTokenIssuer idTokenIssuer;
	private final long accessTokenTtlSeconds;

	public RefreshTokenGrantService(
			TokenStateClient tokenStateClient,
			AccessTokenIssuer accessTokenIssuer,
			IdTokenIssuer idTokenIssuer,
			@Value("${my.access-token-ttl-seconds}") long accessTokenTtlSeconds
	) {
		this.tokenStateClient = tokenStateClient;
		this.accessTokenIssuer = accessTokenIssuer;
		this.idTokenIssuer = idTokenIssuer;
		this.accessTokenTtlSeconds = accessTokenTtlSeconds;
	}

	public GrantResult grant(ClientInfo client, String refreshToken, String requestedScope) {

		if (!client.grantTypes().contains("refresh_token")) {
			return GrantResult.failed("unauthorized_client", "client not authorized for refresh_token grant");
		}
		if (!StringUtils.hasText(refreshToken)) {
			return GrantResult.failed("invalid_request", "refresh_token is required");
		}

		RotateResult rotation = tokenStateClient.rotate(refreshToken, client.clientId());
		if (rotation == null || !rotation.isRotated()) {
			String status = (rotation == null) ? "NULL" : rotation.status();
			log.info("refresh rotation rejected. clientId={} status={}", client.clientId(), status);
			return GrantResult.failed("invalid_grant", "refresh token is not valid");
		}

		List<String> storedScopes = Arrays.asList(rotation.scope().split(" "));
		String effectiveScope = rotation.scope();
		if (StringUtils.hasText(requestedScope)) {
			List<String> requested = Arrays.asList(requestedScope.trim().split("\\s+"));
			if (!storedScopes.containsAll(requested)) {
				return GrantResult.failed("invalid_scope", "requested scope exceeds the original grant");
			}
			effectiveScope = String.join(" ", requested);
		}

		String accessToken = accessTokenIssuer.issue(rotation.sub(), client.clientId(), effectiveScope);

		String idToken = null;
		if (Arrays.asList(effectiveScope.split(" ")).contains("openid")) {
			// nonce 는 넣지 않는다. 원래 authorization 요청에 묶인 값이라 재발급 토큰에 실으면 리플레이 방어가 깨진다.
			// auth_time 은 최초 인증 시각을 그대로 유지한다. (OIDC Core 12.2)
			idToken = idTokenIssuer.issue(rotation.sub(), client.clientId(), effectiveScope,
					null, rotation.authTime(), accessToken);
		}

		return GrantResult.ok(new TokenResponse(accessToken, "Bearer", accessTokenTtlSeconds,
				effectiveScope, idToken, rotation.refreshToken()));
	}
}
