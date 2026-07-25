package dev.starryeye.token;

import dev.starryeye.token.client.UserDirectoryClient;
import dev.starryeye.token.client.UserProfile;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class UserInfoController {

	/**
	 * OIDC userinfo 엔드포인트이다. access token 으로 인증하고 scope 에 대응하는 claim 만 돌려준다.
	 *      sub 는 항상 포함한다(표준 필수). profile/email scope 가 없으면 user-directory 를 조회하지도 않는다.
	 *      claim 매핑은 ProfileClaimMapper 로 id token 과 공유한다.
	 *      에러는 RFC 6750 형식으로 WWW-Authenticate 헤더에 담는다.
	 *
	 * 주의. OIDC Core 5.3.1 은 GET 과 POST 를 모두 지원할 것을 요구한다(MUST). discovery 가 userinfo_endpoint 를
	 *      광고하는 이상 표준대로 POST 로 붙는 RP 가 405 를 받으면 안 된다.
	 *
	 * 주의. RFC 6750 §3.1 은 토큰 전달 방식으로 (1) Authorization 헤더, (2) form-encoded body 의 access_token
	 *      파라미터, (3) URI 쿼리의 access_token 파라미터 세 가지를 정의한다. 이 서버는 그 중 (3) 쿼리 파라미터는
	 *      받지 않는다 — 쿼리스트링은 프록시·서버 접근 로그와 Referer 헤더(다음 요청으로 전파)에 그대로 남아
	 *      토큰이 노출되기 때문이다. 쿼리에 access_token 이 실려 있으면 GET/POST 를 가리지 않고 유효한 전달로
	 *      인정하지 않는다.
	 *      한 요청이 두 방식(헤더+폼, 헤더+쿼리)을 동시에 쓰는 것도 RFC 6750 상 오류이므로 400 invalid_request 로
	 *      거절한다.
	 *
	 * 주의. form-encoded POST 라도 access_token 이 쿼리스트링에도 실려 있으면 서블릿이 쿼리·폼 파라미터를 같은
	 *      이름끼리 병합하므로 {@code @RequestParam} 값만으로는 그 값이 폼에서 온 것인지 쿼리에서 온 것인지
	 *      구분할 수 없다. 그래서 {@link HttpServletRequest#getQueryString()} 의 raw 쿼리를 먼저 파싱해 쿼리에
	 *      access_token 이 있는지를 직접 판정한다("access_token=" 부분 문자열 매칭은 my_access_token= 같은 값에
	 *      오탐하므로 & 로 분리한 뒤 파라미터 이름을 디코딩해 정확히 비교한다).
	 *
	 * 주의. user-directory 조회 실패는 두 갈래로 갈린다.
	 *      404(사용자가 없다는 확정된 사실)면 그 토큰의 주체가 사라진 것이므로 401 invalid_token 이다.
	 *      그 외 장애(연결 실패·5xx)는 존재 여부 미확정이므로 200 {sub} 로 degrade 한다(sub 는 표준 필수 claim 이다).
	 */

	private static final String ACCESS_TOKEN_PARAM = "access_token";

	private final AccessTokenVerifier accessTokenVerifier;
	private final UserDirectoryClient userDirectoryClient;
	private final ProfileClaimMapper profileClaimMapper;

	@RequestMapping(
			value = "/userinfo",
			method = {RequestMethod.GET, RequestMethod.POST},
			produces = "application/json")
	public ResponseEntity<?> userinfo(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@RequestParam(value = ACCESS_TOKEN_PARAM, required = false) String accessTokenParameter,
			HttpServletRequest request
	) {
		String headerToken = (authorization != null && authorization.startsWith("Bearer "))
				? authorization.substring(7) : null;

		boolean queryHasAccessToken = queryStringHasAccessToken(request);
		// 폼 전달은 form-encoded POST 이면서 쿼리스트링에 access_token 이 없을 때만 인정한다.
		// 쿼리에도 실려 있으면 서블릿이 병합한 값이라 폼에서 온 값인지 구분할 수 없으므로 아예 받지 않는다.
		String formToken = (isFormEncodedPost(request) && !queryHasAccessToken) ? accessTokenParameter : null;

		if (headerToken != null && (formToken != null || queryHasAccessToken)) {
			// 헤더 + (폼 또는 쿼리) 동시 사용 -> RFC 6750 §3.1 위반
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_request\"").build();
		}

		if (queryHasAccessToken) {
			// 헤더 없이 쿼리로만 온 access_token -> 이 서버가 받지 않는 전달 방식이다 (메서드 무관)
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").build();
		}

		String token = (headerToken != null) ? headerToken : formToken;
		if (token == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").build();
		}

		AccessTokenVerifier.VerifiedToken verified;
		try {
			verified = accessTokenVerifier.verify(token);
		} catch (AccessTokenVerifier.InvalidTokenException e) {
			// 토큰이 무효한 경우만 401 이다. verifier 의 그 외 예외(signing 장애 등)는 전파해 500 server_error 가 된다.
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"").build();
		}

		if (!verified.scopes().contains("openid")) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\"").build();
		}

		List<String> scopes = verified.scopes();
		UserProfile profile = null;
		if (profileClaimMapper.needsProfileLookup(scopes)) {
			try {
				profile = userDirectoryClient.getUser(verified.sub());
			} catch (UserDirectoryClient.UserNotFoundException e) {
				// 확정된 부재 -> 주체가 사라진 토큰이므로 실효 처리
				return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
						.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"").build();
			} catch (Exception e) {
				// 일시적 조회 불가(사용자 존재 여부 미확정) -> sub 만으로 degrade
				log.warn("user-directory 조회 실패. 프로필 claim 없이 userinfo 를 반환한다. sub={}", verified.sub());
			}
		}

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("sub", verified.sub()); // 표준 필수
		response.putAll(profileClaimMapper.toClaims(scopes, profile));

		return ResponseEntity.ok(response);
	}

	private boolean isFormEncodedPost(HttpServletRequest request) {
		if (!HttpMethod.POST.matches(request.getMethod())) {
			return false;
		}
		String contentType = request.getContentType();
		return contentType != null && contentType.toLowerCase()
				.startsWith(MediaType.APPLICATION_FORM_URLENCODED_VALUE); // charset 등 파라미터가 붙을 수 있다
	}

	// raw 쿼리스트링에 access_token 파라미터가 있는지 판정한다. 서블릿이 병합해주는 @RequestParam 값에는
	// 쿼리와 폼 중 어디서 왔는지가 지워지므로 getQueryString() 을 직접 봐야 한다.
	// & 로 먼저 분리하고 파라미터 이름만 디코딩해 비교해, "my_access_token=" 같은 값에 오탐하지 않는다.
	private boolean queryStringHasAccessToken(HttpServletRequest request) {
		String queryString = request.getQueryString();
		if (queryString == null || queryString.isEmpty()) {
			return false;
		}
		for (String pair : queryString.split("&")) {
			if (pair.isEmpty()) {
				continue;
			}
			String name = pair.split("=", 2)[0];
			if (ACCESS_TOKEN_PARAM.equals(URLDecoder.decode(name, StandardCharsets.UTF_8))) {
				return true;
			}
		}
		return false;
	}
}
