package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.RotateResult;
import dev.starryeye.token.client.TokenStateClient;
import dev.starryeye.token.client.UserDirectoryClient;
import dev.starryeye.token.dto.TokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;

@Slf4j
@Service
public class RefreshTokenGrantService {

	/**
	 * refresh grant 를 처리한다. 회전은 token-state 에 한 번의 호출로 위임하고, 결과로 새 access token 과
	 *      (openid 가 있으면) id token 을 조립한다.
	 *
	 * 주의. 회전 실패 사유를 전부 invalid_grant 하나로 뭉갠다. "이미 소진됐다" 와 "그런 토큰 없다" 를 구분해 주면
	 *      공격자가 토큰의 상태를 탐색할 수 있다. 구분은 로그에만 남긴다. 유일한 예외가 SCOPE_EXCEEDED 이며,
	 *      RFC 6749 6 이 축소 요청 초과를 invalid_scope 로 규정한다.
	 *
	 * 주의. 축소 요청은 검사하지 않고 그대로 token-state 에 넘긴다. 여기서 검사하려면 저장된 scope 를 알아야 하고,
	 *      그건 회전 응답에서만 나오므로 검사 시점이 이미 이전 토큰이 소진된 뒤가 된다. 그 자리에서 거절하면
	 *      새 토큰 원문을 버리게 되고(원문은 그 응답에만 있다), client 가 이전 토큰으로 재시도하면 재사용 탐지에
	 *      걸려 계열이 죽는다 — scope 오타 한 번이 grant 를 파괴한다. 그래서 판단을 상태 소유자 쪽에 둔다.
	 *
	 * 주의. scope 축소(RFC 6749 6)는 이번 access token 에만 적용된다. 저장된 refresh 의 scope 는 token-state 가
	 *      그대로 유지하므로, 한 번 좁혀도 다음 회전에서 원래 범위로 돌아온다.
	 *
	 * 주의. token-state 가 빈 본문을 주면(역직렬화 결과 null) 회전 실패로 다루지 않고 예외를 올려 server_error 가
	 *      되게 한다. "우리가 확인하지 못했다" 를 "네 토큰이 나쁘다" 로 말하면, client 는 멀쩡한 refresh token 을
	 *      버리고 재인증을 돌린다.
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

		RotateResult rotation = tokenStateClient.rotate(refreshToken, client.clientId(), requestedScope);
		if (rotation == null) {
			throw new IllegalStateException("token-state returned an empty rotation response");
		}
		if (rotation.isScopeExceeded()) {
			// 상태는 그대로다. client 는 같은 refresh token 으로 올바른 scope 를 다시 보낼 수 있다.
			return GrantResult.failed("invalid_scope", "requested scope exceeds the original grant");
		}
		if (!rotation.isRotated()) {
			log.info("refresh rotation rejected. clientId={} status={}", client.clientId(), rotation.status());
			return GrantResult.failed("invalid_grant", "refresh token is not valid");
		}

		// 부분집합 검사는 token-state 가 이미 끝냈다. 여기서는 와이어 포맷만 정규화한다.
		String effectiveScope = StringUtils.hasText(requestedScope)
				? String.join(" ", requestedScope.trim().split("\\s+"))
				: rotation.scope();

		String accessToken = accessTokenIssuer.issue(rotation.sub(), client.clientId(), effectiveScope);

		String idToken = null;
		if (Arrays.asList(effectiveScope.split(" ")).contains("openid")) {
			try {
				// nonce 는 넣지 않는다. 원래 authorization 요청에 묶인 값이라 재발급 토큰에 실으면 리플레이 방어가 깨진다.
				// auth_time 은 최초 인증 시각을 그대로 유지한다. (OIDC Core 12.2)
				idToken = idTokenIssuer.issue(rotation.sub(), client.clientId(), effectiveScope,
						null, rotation.authTime(), accessToken);
			} catch (UserDirectoryClient.UserNotFoundException e) {
				// 회전 후 사용자가 삭제된 경우다. code 교환 경로와 같은 판단을 한다 — 존재하지 않는 주체에 대한
				// 인증 주장(id token)을 만들 수 없으므로 grant 자체를 무효로 본다. 사용자가 사라졌다는 확정된
				// 사실을 server_error 로 내면 client 는 재시도로 해결될 장애로 읽는다.
				return GrantResult.failed("invalid_grant", "subject of the grant no longer exists");
			}
		}

		return GrantResult.ok(new TokenResponse(accessToken, "Bearer", accessTokenTtlSeconds,
				effectiveScope, idToken, rotation.refreshToken()));
	}
}
