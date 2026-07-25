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
	 * 주의. RFC 6750 은 토큰 전달 방식으로 Authorization 헤더 / form-encoded body / URI 쿼리 셋을 정의하되
	 *      쿼리 파라미터는 권장하지 않는다(로그·Referer·브라우저 히스토리에 토큰이 남는다). 그래서 헤더와 폼만 받는다.
	 *      한 요청이 두 방식을 동시에 쓰는 것도 RFC 6750 상 오류이므로 400 invalid_request 로 거절한다.
	 *
	 * 주의. user-directory 조회 실패는 두 갈래로 갈린다.
	 *      404(사용자가 없다는 확정된 사실)면 그 토큰의 주체가 사라진 것이므로 401 invalid_token 이다.
	 *      그 외 장애(연결 실패·5xx)는 존재 여부 미확정이므로 200 {sub} 로 degrade 한다(sub 는 표준 필수 claim 이다).
	 */

	private final AccessTokenVerifier accessTokenVerifier;
	private final UserDirectoryClient userDirectoryClient;
	private final ProfileClaimMapper profileClaimMapper;

	@RequestMapping(
			value = "/userinfo",
			method = {RequestMethod.GET, RequestMethod.POST},
			produces = "application/json")
	public ResponseEntity<?> userinfo(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@RequestParam(value = "access_token", required = false) String accessTokenParameter,
			HttpServletRequest request
	) {
		String headerToken = (authorization != null && authorization.startsWith("Bearer "))
				? authorization.substring(7) : null;
		// 폼 전달은 form-encoded POST 일 때만 인정한다 (GET 의 쿼리 파라미터는 받지 않는다)
		String formToken = isFormEncodedPost(request) ? accessTokenParameter : null;

		if (headerToken != null && formToken != null) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_request\"").build();
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
}
