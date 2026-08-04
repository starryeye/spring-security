package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.SessionClient;
import dev.starryeye.token.client.SigningClient;
import dev.starryeye.token.client.TokenStateClient;
import dev.starryeye.token.client.UserDirectoryClient;
import dev.starryeye.token.dto.OAuth2ErrorResponse;
import dev.starryeye.token.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class TokenEndpointController {

	/**
	 * authorization_code 와 refresh_token 두 grant 를 access token 으로 교환한다. (직접 구현, SAS starter 미사용)
	 *      authorization_code 절차: client 인증(Basic) -> code 1회 소비(Redis GETDEL) -> code 바인딩/PKCE 대조
	 *      -> 표준 claim 구성 -> signing 위임 -> JWT 응답. refresh_token 은 RefreshTokenGrantService 에 위임한다.
	 *      jwks 는 signing 이 소유하므로 프록시로 노출하고, 메타데이터 엔드포인트는 정적으로 구성한다.
	 *
	 * 주의. grant type 검사는 client 인증 다음에 한다. 순서가 반대면 인증되지 않은 요청도 이 서버가 어떤 grant 를
	 *      지원하는지 알아낼 수 있다.
	 */

	private final AuthorizationCodeStore codeStore;
	private final PkceValidator pkceValidator;
	private final ClientAuthenticator clientAuthenticator;
	private final SigningClient signingClient;
	private final AccessTokenIssuer accessTokenIssuer;
	private final IdTokenIssuer idTokenIssuer;
	private final TokenStateClient tokenStateClient;
	private final RefreshTokenGrantService refreshTokenGrantService;
	private final ClientCredentialsGrantService clientCredentialsGrantService;
	private final SessionClient sessionClient;

	@Value("${my.issuer}")
	private String issuer;

	@Value("${my.access-token-ttl-seconds}")
	private long accessTokenTtlSeconds;

	@PostMapping(value = "/oauth2/token", produces = "application/json")
	public ResponseEntity<?> token(
			@RequestHeader(value = "Authorization", required = false) String authorization,
			@RequestParam("grant_type") String grantType,
			@RequestParam(value = "code", required = false) String code,
			@RequestParam(value = "redirect_uri", required = false) String redirectUri,
			@RequestParam(value = "code_verifier", required = false) String codeVerifier,
			@RequestParam(value = "refresh_token", required = false) String refreshTokenParam,
			@RequestParam(value = "scope", required = false) String scopeParam
	) {
		// 1. client 인증 (Basic) — grant type 검사보다 먼저 한다. 순서가 반대면 인증되지 않은 요청도
		// "이 서버가 어떤 grant 를 지원하는지" 알아낼 수 있다.
		ClientInfo client;
		try {
			client = clientAuthenticator.authenticate(authorization);
		} catch (ClientAuthenticator.ClientAuthenticationException e) {
			return error(HttpStatus.UNAUTHORIZED, "invalid_client", e.getMessage());
		}

		if (!"authorization_code".equals(grantType) && !"refresh_token".equals(grantType)
				&& !"client_credentials".equals(grantType)) {
			return error(HttpStatus.BAD_REQUEST, "unsupported_grant_type",
					"only authorization_code, refresh_token and client_credentials are supported");
		}

		if ("refresh_token".equals(grantType)) {
			GrantResult result = refreshTokenGrantService.grant(client, refreshTokenParam, scopeParam);
			if (!result.success()) {
				// unauthorized_client · invalid_grant · invalid_scope · invalid_request 는 RFC 6749 5.2 상 모두 400 이다
				return error(HttpStatus.BAD_REQUEST, result.error(), result.errorDescription());
			}
			return ResponseEntity.ok(result.response());
		}

		if ("client_credentials".equals(grantType)) {
			GrantResult result = clientCredentialsGrantService.grant(client, scopeParam);
			if (!result.success()) {
				return error(HttpStatus.BAD_REQUEST, result.error(), result.errorDescription());
			}
			return ResponseEntity.ok(result.response());
		}

		if (!client.grantTypes().contains("authorization_code")) {
			return error(HttpStatus.BAD_REQUEST, "unauthorized_client", "client not authorized for authorization_code grant");
		}

		// 2. code 소비 (1회용, 원자적)
		Optional<AuthorizationCodeData> maybeData = (code == null) ? Optional.empty() : codeStore.consume(code);
		if (maybeData.isEmpty()) {
			return error(HttpStatus.BAD_REQUEST, "invalid_grant", "code invalid or expired");
		}
		AuthorizationCodeData data = maybeData.get();

		// 3. code 바인딩 검증 (client, redirect_uri)
		if (!data.clientId().equals(client.clientId()) || !data.redirectUri().equals(redirectUri)) {
			return error(HttpStatus.BAD_REQUEST, "invalid_grant", "code binding mismatch");
		}

		// 4. PKCE 대조
		if (codeVerifier == null || !pkceValidator.matches(codeVerifier, data.codeChallenge())) {
			return error(HttpStatus.BAD_REQUEST, "invalid_grant", "PKCE verification failed");
		}

		// 5. 표준 claim 구성 + signing 위임 (authorization_code 와 refresh_token 이 같은 claim 집합을 내도록
		// AccessTokenIssuer 로 위임한다 — 두 grant 에서 각각 인라인으로 구성하면 한쪽만 고쳤을 때 조용히 갈라진다)
		String jwt = accessTokenIssuer.issue(data.sub(), client.clientId(), data.scope());

		// openid scope 요청 시 id token 을 함께 발급한다 (OIDC)
		String idToken = null;
		if (Arrays.asList(data.scope().split(" ")).contains("openid")) {
			try {
				idToken = idTokenIssuer.issue(data.sub(), client.clientId(), data.scope(),
						data.nonce(), data.authTime(), jwt, data.sid());
			} catch (UserDirectoryClient.UserNotFoundException e) {
				// code 발급 후 사용자가 삭제된 경우다. 존재하지 않는 주체에 대한 인증 주장(id token)을 만들 수 없으므로
				// grant 자체를 무효로 본다. code 는 이미 소비됐으니 재시도로 우회되지 않는다.
				return error(HttpStatus.BAD_REQUEST, "invalid_grant", "subject of the grant no longer exists");
			}

			// RP 세션은 id token 을 내주는 이 순간 선다. sid 가 없으면(구버전 code 레코드) 등록할 것이 없다.
			if (StringUtils.hasText(data.sid())) {
				sessionClient.register(data.sid(), data.sub(), client.clientId());
			}
		}

		// offline_access 동의 + refresh_token grant 등록, 둘 다 있어야 refresh token 을 발급한다.
		// 앞은 사용자가 준 허락이고 뒤는 관리자가 정한 client 능력이다. 서로 다른 질문이라 둘 다 본다.
		// grant 등록 없이 발급하면 client 가 평생 쓸 수 없는 토큰을 쥐게 된다.
		String refreshToken = null;
		List<String> grantedScopes = Arrays.asList(data.scope().split(" "));
		if (grantedScopes.contains("offline_access") && client.grantTypes().contains("refresh_token")) {
			refreshToken = tokenStateClient
					.issue(client.clientId(), data.sub(), data.scope(), data.authTime())
					.refreshToken();
		}

		return ResponseEntity.ok(new TokenResponse(jwt, "Bearer", accessTokenTtlSeconds, data.scope(),
				idToken, refreshToken));
	}

	@GetMapping("/oauth2/jwks")
	public Map<?, ?> jwks() {
		return signingClient.jwks();
	}

	/**
	 * 주의. introspection_endpoint_auth_methods_supported 를 내보내지 않는다. RFC 8414 의 그 필드는
	 *      client 인증 방식(client_secret_basic 등)을 담는데, 이 엔드포인트는 Bearer 토큰과 introspect scope 를
	 *      요구하므로 담을 값이 없다. "none" 은 인증이 필요 없다는 거짓이 된다.
	 *      discovery 에는 "Bearer 토큰 + 특정 scope" 를 표현할 표준 필드가 없다.
	 *
	 * 주의. scopes_supported 에 introspect 가 들어가지만, discovery 에는 그것이 사용자 위임 가능한지
	 *      client 자체 능력인지 구분할 필드가 없다. client 가 authorization_code 로 요청할 수 있다고
	 *      오해할 여지가 남는다.
	 */
	@GetMapping({"/.well-known/oauth-authorization-server", "/.well-known/openid-configuration"})
	public Map<String, Object> metadata() {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("issuer", issuer);
		metadata.put("token_endpoint", issuer + "/oauth2/token");
		metadata.put("authorization_endpoint", issuer + "/oauth2/authorize");
		metadata.put("jwks_uri", issuer + "/oauth2/jwks");
		metadata.put("userinfo_endpoint", issuer + "/userinfo");
		metadata.put("introspection_endpoint", issuer + "/oauth2/introspect");
		metadata.put("revocation_endpoint", issuer + "/oauth2/revoke");
		metadata.put("code_challenge_methods_supported", List.of("S256"));
		metadata.put("grant_types_supported", List.of("authorization_code", "refresh_token", "client_credentials"));
		metadata.put("response_types_supported", List.of("code"));
		metadata.put("subject_types_supported", List.of("public"));
		metadata.put("id_token_signing_alg_values_supported", List.of("RS256"));
		metadata.put("revocation_endpoint_auth_methods_supported", List.of("client_secret_basic"));
		metadata.put("scopes_supported", List.of("openid", "profile", "email", "offline_access", "introspect"));
		metadata.put("claims_supported", List.of("sub", "iss", "aud", "exp", "iat", "auth_time", "nonce", "at_hash",
				"name", "nickname", "preferred_username", "email", "email_verified"));
		return metadata;
	}

	private ResponseEntity<OAuth2ErrorResponse> error(HttpStatus status, String error, String description) {
		return ResponseEntity.status(status).body(new OAuth2ErrorResponse(error, description));
	}
}
