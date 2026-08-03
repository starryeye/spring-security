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
import java.util.HashSet;
import java.util.Set;

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
	 *
	 * 주의. ROTATED 를 받은 뒤 effectiveScope 가 rotation.scope()(token-state 가 돌려준 저장 scope)의 부분집합인지
	 *      다시 한 번 방어적으로 확인한다. 축소 검증은 원래 token-state 가 회전과 같은 트랜잭션에서 끝낸다
	 *      (SCOPE_EXCEEDED). 하지만 구버전 token-state 는 RotateRequest 에 requestedScope 필드가 없어(2필드 계약),
	 *      Spring 기본 설정(FAIL_ON_UNKNOWN_PROPERTIES=false) 아래서 이 필드를 역직렬화 시 조용히 무시하고 축소
	 *      없는 평범한 회전으로 ROTATED + 저장 scope 전체를 돌려줄 수 있다 — 롤링 배포 중 token-state 가 token
	 *      보다 먼저 신버전으로 올라가지 않으면 이 창이 열린다. 위반이면 invalid_scope 가 아니라 예외를 던져
	 *      server_error 가 되게 한다 — 이건 client 의 잘못이 아니라 하위 서비스가 계약을 지키지 않은 상황이고,
	 *      회전은 이미 일어났으므로 client 에게 "네 scope 요청이 잘못됐다"고 말하면 오해를 준다.
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

		if (!isSubsetOf(effectiveScope, rotation.scope())) {
			// 구버전 token-state 가 requestedScope 를 무시하고 ROTATED 를 준 것으로 의심되는 상황이다.
			// 원인을 로그로 추적할 수 있도록 두 scope 값을 그대로 남긴다.
			throw new IllegalStateException("token-state contract violation: effectiveScope '" + effectiveScope
					+ "' is not a subset of rotation-reported stored scope '" + rotation.scope()
					+ "' for clientId=" + client.clientId()
					+ " -- token-state may be a stale version that silently ignores requestedScope");
		}

		String accessToken = accessTokenIssuer.issue(rotation.sub(), client.clientId(), effectiveScope);

		String idToken = null;
		if (Arrays.asList(effectiveScope.split(" ")).contains("openid")) {
			try {
				// nonce 는 넣지 않는다. 원래 authorization 요청에 묶인 값이라 재발급 토큰에 실으면 리플레이 방어가 깨진다.
				// auth_time 은 최초 인증 시각을 그대로 유지한다. (OIDC Core 12.2)
				// 주의. refresh 경로에는 sid 가 없다. refresh token 레코드가 sid 를 보관하지 않기 때문이며,
				//      그 결과 refresh 로 재발급한 id token 에는 sid 가 빠진다(알려진 한계).
				idToken = idTokenIssuer.issue(rotation.sub(), client.clientId(), effectiveScope,
						null, rotation.authTime(), accessToken, null);
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

	/**
	 * candidateScope 의 모든 scope 가 storedScope 안에 있는지 본다(공백 구분, 둘 다 이미 정규화된 값).
	 */
	private boolean isSubsetOf(String candidateScope, String storedScope) {
		Set<String> stored = new HashSet<>(Arrays.asList(storedScope.split(" ")));
		return stored.containsAll(Arrays.asList(candidateScope.split(" ")));
	}
}
