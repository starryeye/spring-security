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
	 *      scope 에 따라 profile/email claim 을 덧붙인다. (매핑은 ProfileClaimMapper 가 userinfo 와 공유한다)
	 *
	 * 주의. user-directory 의 실패는 두 갈래로 갈린다.
	 *      404(사용자가 없다는 확정된 사실)면 발급을 중단하고 UserNotFoundException 을 전파한다.
	 *      존재하지 않는 주체에 대해 "이 사람이 로그인했다"는 인증 주장을 서명해 내보낼 수 없기 때문이다.
	 *      그 외 장애(연결 실패·5xx)는 사용자 존재 여부가 미확정이므로 프로필 claim 없이 발급을 계속한다.
	 *      필수 claim 만으로도 표준상 유효한 id token 이므로, 프로필 조회 실패로 인증까지 막지 않는다.
	 *
	 * 주의. sid 는 authorize 시점의 OP 세션 식별자를 그대로 나른다. 여기서 새로 만들면 RP 가 색인해 둔 값과
	 *      어긋나 로그아웃 통지가 그 세션을 찾지 못한다.
	 */

	// OIDC Core 는 id token 에 별도 typ 을 요구하지 않는다. 일반 JWT 로 둔다.
	private static final String ID_TOKEN_TYP = "JWT";

	private final SigningClient signingClient;
	private final UserDirectoryClient userDirectoryClient;
	private final ProfileClaimMapper profileClaimMapper;
	private final String issuer;
	private final long idTokenTtlSeconds;

	public IdTokenIssuer(
			SigningClient signingClient,
			UserDirectoryClient userDirectoryClient,
			ProfileClaimMapper profileClaimMapper,
			@Value("${my.issuer}") String issuer,
			@Value("${my.id-token-ttl-seconds}") long idTokenTtlSeconds
	) {
		this.signingClient = signingClient;
		this.userDirectoryClient = userDirectoryClient;
		this.profileClaimMapper = profileClaimMapper;
		this.issuer = issuer;
		this.idTokenTtlSeconds = idTokenTtlSeconds;
	}

	/**
	 * scope 는 공백 구분 문자열이며 호출부(TokenEndpointController)가 code 레코드에서 읽어 그대로 넘긴다. (null 이 올 수 없는 계약)
	 */
	public String issue(String sub, String clientId, String scope, String nonce, long authTime, String accessToken, String sid) {

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
		if (StringUtils.hasText(sid)) {
			claims.put("sid", sid); // RP 가 자기 세션을 색인하는 키다 (Back-Channel Logout 1.0)
		}

		List<String> scopes = Arrays.asList(scope.split(" "));
		if (profileClaimMapper.needsProfileLookup(scopes)) {
			claims.putAll(profileClaimMapper.toClaims(scopes, lookupProfile(sub)));
		}

		return signingClient.sign(claims, ID_TOKEN_TYP);
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

	private UserProfile lookupProfile(String sub) {
		try {
			return userDirectoryClient.getUser(sub);
		} catch (UserDirectoryClient.UserNotFoundException e) {
			// 확정된 부재 -> 발급 중단. 호출부가 invalid_grant 로 바꾼다.
			throw e;
		} catch (Exception e) {
			// 일시적 조회 불가(사용자 존재 여부 미확정) -> 프로필만 degrade
			log.warn("user-directory 조회 실패. 프로필 claim 없이 id token 을 발급한다. sub={}", sub);
			return null;
		}
	}
}
