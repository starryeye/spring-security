package dev.starryeye.token;

import dev.starryeye.token.client.UserDirectoryClient;
import dev.starryeye.token.client.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class UserInfoController {

	/**
	 * OIDC userinfo 엔드포인트이다. access token 으로 인증하고 scope 에 대응하는 claim 만 돌려준다.
	 *      sub 는 항상 포함한다(표준 필수). profile/email scope 가 없으면 해당 claim 은 응답에서 제외한다.
	 *      에러는 RFC 6750 형식으로 WWW-Authenticate 헤더에 담는다.
	 */

	private final AccessTokenVerifier accessTokenVerifier;
	private final UserDirectoryClient userDirectoryClient;

	@GetMapping(value = "/userinfo", produces = "application/json")
	public ResponseEntity<?> userinfo(@RequestHeader(value = "Authorization", required = false) String authorization) {

		if (authorization == null || !authorization.startsWith("Bearer ")) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").build();
		}

		AccessTokenVerifier.VerifiedToken verified;
		try {
			verified = accessTokenVerifier.verify(authorization.substring(7));
		} catch (AccessTokenVerifier.InvalidTokenException e) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"").build();
		}

		if (!verified.scopes().contains("openid")) {
			return ResponseEntity.status(HttpStatus.FORBIDDEN)
					.header(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"insufficient_scope\"").build();
		}

		Map<String, Object> response = new LinkedHashMap<>();
		response.put("sub", verified.sub()); // 표준 필수

		UserProfile profile = userDirectoryClient.getUser(verified.sub());
		if (profile != null) {
			List<String> scopes = verified.scopes();
			if (scopes.contains("profile")) {
				putIfPresent(response, "name", profile.name());
				putIfPresent(response, "nickname", profile.nickname());
				putIfPresent(response, "preferred_username", profile.preferredUsername());
			}
			if (scopes.contains("email")) {
				putIfPresent(response, "email", profile.email());
				response.put("email_verified", profile.emailVerified());
			}
		}

		return ResponseEntity.ok(response);
	}

	private void putIfPresent(Map<String, Object> response, String key, String value) {
		if (StringUtils.hasText(value)) {
			response.put(key, value);
		}
	}
}
