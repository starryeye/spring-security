package dev.starryeye.token;

import dev.starryeye.token.client.SigningClient;
import dev.starryeye.token.client.UserDirectoryClient;
import dev.starryeye.token.client.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class IdTokenIssuer {

	/**
	 * id token 을 만들어 signing 에 서명을 위임한다. (openid scope 요청 시에만 호출된다)
	 *      필수 claim(iss/sub/aud/exp/iat)에 더해 nonce(요청에 있었으면), auth_time, at_hash 를 담고..
	 *      scope 에 따라 profile/email claim 을 덧붙인다.
	 *
	 * 주의. user-directory 가 응답하지 않아도 id token 발급 자체는 계속한다.
	 *      필수 claim 만으로도 표준상 유효한 id token 이므로, 인증(누가 로그인했는가)을 프로필 조회 실패로 막지 않는다.
	 */

	private final SigningClient signingClient;
	private final UserDirectoryClient userDirectoryClient;
	private final String issuer;
	private final long idTokenTtlSeconds;

	public IdTokenIssuer(
			SigningClient signingClient,
			UserDirectoryClient userDirectoryClient,
			@Value("${my.issuer}") String issuer,
			@Value("${my.id-token-ttl-seconds}") long idTokenTtlSeconds
	) {
		this.signingClient = signingClient;
		this.userDirectoryClient = userDirectoryClient;
		this.issuer = issuer;
		this.idTokenTtlSeconds = idTokenTtlSeconds;
	}

	public String issue(String sub, String clientId, String scope, String nonce, long authTime, String accessToken) {

		Instant now = Instant.now();
		Map<String, Object> claims = new LinkedHashMap<>();
		claims.put("iss", issuer);
		claims.put("sub", sub);
		claims.put("aud", clientId);
		claims.put("iat", now.getEpochSecond());
		claims.put("exp", now.plusSeconds(idTokenTtlSeconds).getEpochSecond());
		claims.put("auth_time", authTime);
		claims.put("at_hash", computeAtHash(accessToken));
		if (StringUtils.hasText(nonce)) {
			claims.put("nonce", nonce); // 요청에 있었으면 그대로 되돌려준다 (표준 요구)
		}

		List<String> scopes = Arrays.asList(scope.split(" "));
		if (scopes.contains("profile") || scopes.contains("email")) {
			addProfileClaims(claims, sub, scopes);
		}

		return signingClient.sign(claims);
	}

	/**
	 * at_hash = BASE64URL( SHA-256(access_token) 의 좌측 절반 ). (alg 가 RS256 이므로 SHA-256)
	 */
	public String computeAtHash(String accessToken) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(accessToken.getBytes(StandardCharsets.US_ASCII));
			byte[] leftHalf = Arrays.copyOf(digest, digest.length / 2);
			return Base64.getUrlEncoder().withoutPadding().encodeToString(leftHalf);
		} catch (Exception e) {
			throw new IllegalStateException("failed to compute at_hash", e);
		}
	}

	private void addProfileClaims(Map<String, Object> claims, String sub, List<String> scopes) {
		UserProfile profile;
		try {
			profile = userDirectoryClient.getUser(sub);
		} catch (Exception e) {
			log.warn("user-directory 조회 실패.. 프로필 claim 없이 id token 을 발급한다. sub={}", sub);
			return;
		}
		if (profile == null) {
			return;
		}
		if (scopes.contains("profile")) {
			putIfPresent(claims, "name", profile.name());
			putIfPresent(claims, "nickname", profile.nickname());
			putIfPresent(claims, "preferred_username", profile.preferredUsername());
		}
		if (scopes.contains("email")) {
			putIfPresent(claims, "email", profile.email());
			claims.put("email_verified", profile.emailVerified());
		}
	}

	private void putIfPresent(Map<String, Object> claims, String key, String value) {
		if (StringUtils.hasText(value)) {
			claims.put(key, value);
		}
	}
}
