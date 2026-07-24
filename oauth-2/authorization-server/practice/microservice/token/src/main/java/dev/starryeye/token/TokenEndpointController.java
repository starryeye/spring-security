package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.ClientRegistryClient;
import dev.starryeye.token.client.SigningClient;
import dev.starryeye.token.dto.OAuth2ErrorResponse;
import dev.starryeye.token.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class TokenEndpointController {

	/**
	 * authorization code 를 access token 으로 교환한다. (직접 구현, SAS starter 미사용)
	 *      절차: client 인증(Basic) -> code 1회 소비(Redis GETDEL) -> code 바인딩/PKCE 대조 -> 표준 claim 구성 -> signing 위임 -> JWT 응답.
	 *      jwks 는 signing 이 소유하므로 프록시로 노출하고, 메타데이터 엔드포인트는 정적으로 구성한다.
	 */

	private final AuthorizationCodeStore codeStore;
	private final PkceValidator pkceValidator;
	private final ClientRegistryClient clientRegistryClient;
	private final SigningClient signingClient;
	private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

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
			@RequestParam(value = "code_verifier", required = false) String codeVerifier
	) {
		if (!"authorization_code".equals(grantType)) {
			return error(HttpStatus.BAD_REQUEST, "unsupported_grant_type", "only authorization_code is supported");
		}

		// 1. client 인증 (Basic)
		String[] credentials = parseBasic(authorization);
		if (credentials == null) {
			return error(HttpStatus.UNAUTHORIZED, "invalid_client", "missing client credentials");
		}
		ClientInfo client;
		try {
			client = clientRegistryClient.getClient(credentials[0]);
		} catch (ClientRegistryClient.ClientNotFoundException e) {
			return error(HttpStatus.UNAUTHORIZED, "invalid_client", "unknown client");
		}
		if (client == null || !passwordEncoder.matches(credentials[1], client.clientSecretHash())) {
			return error(HttpStatus.UNAUTHORIZED, "invalid_client", "bad client credentials");
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

		// 5. 표준 claim 구성 + signing 위임
		Instant now = Instant.now();
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("iss", issuer);
		claims.put("sub", data.sub());
		claims.put("aud", client.clientId());
		claims.put("iat", now.getEpochSecond());
		claims.put("exp", now.plusSeconds(accessTokenTtlSeconds).getEpochSecond());
		claims.put("scope", Arrays.asList(data.scope().split(" ")));

		String jwt = signingClient.sign(claims);

		return ResponseEntity.ok(new TokenResponse(jwt, "Bearer", accessTokenTtlSeconds, data.scope()));
	}

	@GetMapping("/oauth2/jwks")
	public Map<?, ?> jwks() {
		return signingClient.jwks();
	}

	@GetMapping("/.well-known/oauth-authorization-server")
	public Map<String, Object> metadata() {
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("issuer", issuer);
		metadata.put("token_endpoint", issuer + "/oauth2/token");
		metadata.put("authorization_endpoint", issuer + "/oauth2/authorize");
		metadata.put("jwks_uri", issuer + "/oauth2/jwks");
		metadata.put("code_challenge_methods_supported", List.of("S256"));
		metadata.put("grant_types_supported", List.of("authorization_code"));
		return metadata;
	}

	private String[] parseBasic(String authorization) {
		if (authorization == null || !authorization.startsWith("Basic ")) {
			return null;
		}
		String decoded;
		try {
			decoded = new String(Base64.getDecoder().decode(authorization.substring(6)), java.nio.charset.StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return null; // 잘못된 base64 -> invalid_client
		}
		int idx = decoded.indexOf(':');
		if (idx < 0) {
			return null;
		}
		return new String[]{decoded.substring(0, idx), decoded.substring(idx + 1)};
	}

	private ResponseEntity<OAuth2ErrorResponse> error(HttpStatus status, String error, String description) {
		return ResponseEntity.status(status).body(new OAuth2ErrorResponse(error, description));
	}
}
