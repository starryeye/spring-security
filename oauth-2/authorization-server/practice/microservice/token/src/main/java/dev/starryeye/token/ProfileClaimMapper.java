package dev.starryeye.token;

import dev.starryeye.token.client.UserProfile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ProfileClaimMapper {

	/**
	 * scope -> claim 매핑을 한 곳에 모은다. id token(IdTokenIssuer)과 userinfo(UserInfoController)가 이 매퍼만 쓴다.
	 *      profile -> name/nickname/preferred_username, email -> email/email_verified.
	 *
	 * 주의. 같은 access token 으로 받은 id token 과 userinfo 응답의 claim 이 서로 다르면 RP 는 둘 중 하나를 신뢰할 수 없다.
	 *      매핑을 양쪽에 각각 구현하면 한쪽만 고쳐질 때 조용히 갈라지므로 매핑과 "조회가 필요한지"의 판단을 함께 여기 둔다.
	 *
	 * 주의. email_verified 는 email 값이 있을 때만 함께 넣는다. 검증 대상인 email 없이 email_verified 만 나가면
	 *      RP 는 무엇이 검증됐다는 것인지 알 수 없다.
	 */

	/**
	 * profile/email claim 이 필요한 요청인지 판단한다. false 면 user-directory 를 호출할 이유가 없다.
	 *      (openid 만 있는 요청이 원격 호출을 하면 불필요한 왕복이자 무관한 가용성 결합이 된다)
	 */
	public boolean needsProfileLookup(Collection<String> scopes) {
		return scopes.contains("profile") || scopes.contains("email");
	}

	/**
	 * scope 에 대응하는 claim 만 담은 Map 을 만든다. profile 이 null 이면 빈 Map 이다(조회 실패 degrade).
	 */
	public Map<String, Object> toClaims(Collection<String> scopes, UserProfile profile) {
		Map<String, Object> claims = new LinkedHashMap<>();
		if (profile == null) {
			return claims;
		}
		if (scopes.contains("profile")) {
			putIfPresent(claims, "name", profile.name());
			putIfPresent(claims, "nickname", profile.nickname());
			putIfPresent(claims, "preferred_username", profile.preferredUsername());
		}
		if (scopes.contains("email") && StringUtils.hasText(profile.email())) {
			claims.put("email", profile.email());
			claims.put("email_verified", profile.emailVerified());
		}
		return claims;
	}

	private void putIfPresent(Map<String, Object> claims, String key, String value) {
		if (StringUtils.hasText(value)) {
			claims.put(key, value);
		}
	}
}
