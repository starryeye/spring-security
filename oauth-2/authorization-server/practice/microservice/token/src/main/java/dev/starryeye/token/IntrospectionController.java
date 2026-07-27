package dev.starryeye.token;

import dev.starryeye.token.client.RefreshTokenInfo;
import dev.starryeye.token.client.TokenStateClient;
import dev.starryeye.token.dto.OAuth2ErrorResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class IntrospectionController {

	/**
	 * 토큰의 활성 여부와 claim 을 돌려준다. (RFC 7662)
	 *
	 * 주의. token_type_hint 는 힌트일 뿐이라 틀릴 수 있다(RFC 7662 2.1). 힌트를 믿고 분기하면 잘못된 힌트가
	 *      멀쩡한 토큰을 비활성으로 만든다. 그래서 JWT 파싱을 먼저 시도하고 실패했을 때만 token-state 에 묻는다.
	 *
	 * 주의. access token 은 로컬 검증만 하고 token-state 를 조회하지 않는다. 이 서버는 폐기를 refresh 한정으로
	 *      정했으므로 access token 의 활성 여부는 서명과 exp 만으로 결정된다.
	 *
	 * 주의. 비활성 응답은 {"active": false} 하나뿐이다(RFC 7662 2.2). 만료 · 폐기 · 형식 오류를 구분해 주면
	 *      토큰을 쥔 공격자가 그 토큰의 내력을 알아낼 수 있다.
	 *
	 * 주의. token-state 가 빈 본문을 주면(역직렬화 결과 null) 그것은 "비활성" 이 아니라 "확인하지 못했다" 이므로
	 *      예외를 올려 server_error 로 끝낸다. {"active": false} 로 degrade 하면 살아있는 토큰을 죽었다고 말하는
	 *      것이라, resource server 가 멀쩡한 요청을 거절한다.
	 *
	 * 주의. 인증된 등록 client 면 누구의 토큰이든 조회할 수 있다. resource server 가 검사하는 토큰은 언제나
	 *      다른 client 에게 발급된 것이므로, "자기 토큰만" 으로 제한하면 기능이 성립하지 않는다.
	 *      권한을 좁히려면 client_credentials grant 로 받은 토큰의 introspect scope 를 보는 것이 정석이며,
	 *      이 서버에는 아직 그 grant 가 없다.
	 */

	private final ClientAuthenticator clientAuthenticator;
	private final AccessTokenVerifier accessTokenVerifier;
	private final TokenStateClient tokenStateClient;

	@Value("${my.issuer}")
	private String issuer;

	@PostMapping(value = "/oauth2/introspect", produces = "application/json")
	public ResponseEntity<?> introspect(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@RequestParam(value = "token", required = false) String token,
			@RequestParam(value = "token_type_hint", required = false) String tokenTypeHint
	) {
		try {
			// 인증 결과는 쓰지 않는다. 소유권 검사를 두지 않는 설계라 "인증된 등록 client 인가" 만 보면 된다.
			clientAuthenticator.authenticate(authorization);
		} catch (ClientAuthenticator.ClientAuthenticationException e) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new OAuth2ErrorResponse("invalid_client", e.getMessage()));
		}
		if (!StringUtils.hasText(token)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(new OAuth2ErrorResponse("invalid_request", "token is required"));
		}

		try {
			AccessTokenVerifier.VerifiedToken verified = accessTokenVerifier.verify(token);
			return ResponseEntity.ok(activeAccessToken(verified));
		} catch (AccessTokenVerifier.InvalidTokenException e) {
			// JWT 가 아니거나 무효다. refresh token 일 수 있으므로 소유자에게 묻는다.
			RefreshTokenInfo info = tokenStateClient.introspect(token);
			if (info == null) {
				throw new IllegalStateException("token-state returned an empty introspection response");
			}
			if (!info.active()) {
				return ResponseEntity.ok(Map.of("active", false));
			}
			return ResponseEntity.ok(activeRefreshToken(info));
		}
	}

	private Map<String, Object> activeAccessToken(AccessTokenVerifier.VerifiedToken verified) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("active", true);
		response.put("sub", verified.sub());
		response.put("client_id", verified.clientId());
		response.put("scope", String.join(" ", verified.scopes()));
		response.put("exp", verified.exp());
		response.put("iat", verified.iat());
		response.put("iss", issuer);
		response.put("token_type", "Bearer"); // RFC 6749 7.1 이 정의하는 access token 의 사용 방식
		return response;
	}

	private Map<String, Object> activeRefreshToken(RefreshTokenInfo info) {
		Map<String, Object> response = new LinkedHashMap<>();
		response.put("active", true);
		response.put("sub", info.sub());
		response.put("client_id", info.clientId());
		response.put("scope", info.scope());
		response.put("exp", info.exp());
		response.put("iat", info.iat());
		response.put("iss", issuer);
		// token_type 은 넣지 않는다. refresh token 은 리소스 접근에 쓰이지 않는다.
		return response;
	}
}
