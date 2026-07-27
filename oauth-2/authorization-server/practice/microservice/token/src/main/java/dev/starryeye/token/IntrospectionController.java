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
	 * 주의. 이 엔드포인트는 client 인증 대상이 아니라 protected resource 다. 호출자는 client_credentials 로
	 *      받은 access token 을 Bearer 로 제시하고, 그 토큰에 introspect scope 가 있어야 한다.
	 *      그래서 오류도 RFC 6749(client 인증)가 아니라 RFC 6750(Bearer)을 따른다 —
	 *      토큰 없음/Basic 은 401, 무효 토큰은 401 invalid_token, scope 부족은 403 insufficient_scope 다.
	 *
	 * 주의. Basic 을 계속 받으면 scope 로 좁힌 의미가 사라진다. 그래서 Basic 은 토큰 없음과 같이 401 이다.
	 *
	 * 주의. revoke 는 여전히 Basic 을 쓴다. 비대칭이 의도적이다 — revoke 는 "자기 토큰을 폐기" 라 소유자
	 *      확인(client 인증)이 맞고, introspect 는 "남의 토큰을 검사" 라 별도로 부여된 능력(scope)이 맞다.
	 *
	 * 주의. 호출자 토큰 검증도 검사 대상 토큰 검증도 같은 AccessTokenVerifier 를 쓴다. jwks 확보 실패는
	 *      InvalidTokenException 이 아닌 예외로 전파돼 500 server_error 가 된다 — 401 로 응답하면 호출자가
	 *      멀쩡한 자기 토큰을 폐기하고 재발급을 돌린다.
	 */

	private static final String REQUIRED_SCOPE = "introspect";

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
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").build();
		}

		AccessTokenVerifier.VerifiedToken caller;
		try {
			caller = accessTokenVerifier.verify(authorization.substring(7));
		} catch (AccessTokenVerifier.InvalidTokenException e) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"").build();
		}
		if (!caller.scopes().contains(REQUIRED_SCOPE)) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\"").build();
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
