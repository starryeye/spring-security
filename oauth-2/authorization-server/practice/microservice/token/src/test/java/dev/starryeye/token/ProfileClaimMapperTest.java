package dev.starryeye.token;

import dev.starryeye.token.client.SigningClient;
import dev.starryeye.token.client.UserDirectoryClient;
import dev.starryeye.token.client.UserProfile;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfileClaimMapperTest {

	private final ProfileClaimMapper mapper = new ProfileClaimMapper();

	private UserProfile profile() {
		return new UserProfile("user-sub-0001", "user", List.of("ROLE_USER"),
				"Star Rye", "starry", "starryeye", "starryeye@example.com", true);
	}

	@Test
	void mapsProfileScopeToNameClaims() {
		Map<String, Object> claims = mapper.toClaims(List.of("openid", "profile"), profile());

		assertThat(claims).containsEntry("name", "Star Rye");
		assertThat(claims).containsEntry("nickname", "starry");
		assertThat(claims).containsEntry("preferred_username", "starryeye");
		assertThat(claims).doesNotContainKeys("email", "email_verified");
	}

	@Test
	void mapsEmailScopeToEmailClaims() {
		Map<String, Object> claims = mapper.toClaims(List.of("openid", "email"), profile());

		assertThat(claims).containsEntry("email", "starryeye@example.com");
		assertThat(claims).containsEntry("email_verified", true);
		assertThat(claims).doesNotContainKey("name");
	}

	// email 값이 없으면 email_verified 도 빼야 한다. 검증 대상 없이 검증 플래그만 나가면 RP 가 해석할 수 없다.
	@Test
	void omitsEmailVerifiedWhenEmailAbsent() {
		UserProfile withoutEmail = new UserProfile("user-sub-0001", "user", List.of("ROLE_USER"),
				"Star Rye", "starry", "starryeye", null, true);

		Map<String, Object> claims = mapper.toClaims(List.of("openid", "email"), withoutEmail);

		assertThat(claims).doesNotContainKeys("email", "email_verified");
	}

	@Test
	void openidOnlyNeedsNoRemoteLookup() {
		assertThat(mapper.needsProfileLookup(List.of("openid"))).isFalse();
		assertThat(mapper.needsProfileLookup(List.of("openid", "profile"))).isTrue();
		assertThat(mapper.needsProfileLookup(List.of("openid", "email"))).isTrue();
	}

	@Test
	void returnsNoClaimsWhenProfileUnavailable() {
		assertThat(mapper.toClaims(List.of("openid", "profile", "email"), null)).isEmpty();
	}

	/**
	 * parity 테스트: 같은 scope·같은 프로필에 대해 id token 과 userinfo 가 같은 profile/email claim 을 내야 한다.
	 *      두 경로가 매핑을 각자 구현하면 한쪽만 고쳐질 때 조용히 갈라지므로, 실제 호출부 두 곳을 나란히 돌려 비교한다.
	 */
	@Test
	void idTokenAndUserinfoEmitIdenticalProfileClaims() {
		List<String> scopes = List.of("openid", "profile", "email");
		UserDirectoryClient userDirectoryClient = mock(UserDirectoryClient.class);
		when(userDirectoryClient.getUser("user-sub-0001")).thenReturn(profile());

		// id token 경로
		SigningClient signingClient = mock(SigningClient.class);
		when(signingClient.sign(anyMap(), any())).thenReturn("signed.jwt.value");
		IdTokenIssuer issuer = new IdTokenIssuer(signingClient, userDirectoryClient, mapper,
				"http://localhost:9000", 300);
		issuer.issue("user-sub-0001", "my-client", String.join(" ", scopes), null, 1700000000L, "at", null);
		ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
		verify(signingClient).sign(captor.capture(), any());
		Map<String, Object> idTokenClaims = captor.getValue();

		// userinfo 경로
		AccessTokenVerifier verifier = mock(AccessTokenVerifier.class);
		when(verifier.verify("tok")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("user-sub-0001", scopes, "my-client", 1800000000L, 1700000000L));
		UserInfoController controller = new UserInfoController(verifier, userDirectoryClient, mapper);
		@SuppressWarnings("unchecked")
		Map<String, Object> userinfo = (Map<String, Object>) controller
				.userinfo("Bearer tok", null, new MockHttpServletRequest("GET", "/userinfo")).getBody();

		List<String> profileClaims = List.of("name", "nickname", "preferred_username", "email", "email_verified");
		// 둘 다 값이 비어 있어서 "같다"가 되는 것을 막는다
		assertThat(idTokenClaims).containsKeys(profileClaims.toArray(new String[0]));
		for (String claim : profileClaims) {
			assertThat(userinfo).containsEntry(claim, idTokenClaims.get(claim));
		}
		assertThat(userinfo).containsEntry("sub", idTokenClaims.get("sub"));
	}
}
