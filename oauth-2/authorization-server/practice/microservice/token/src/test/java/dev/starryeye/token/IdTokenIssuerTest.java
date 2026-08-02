package dev.starryeye.token;

import dev.starryeye.token.client.SigningClient;
import dev.starryeye.token.client.UserDirectoryClient;
import dev.starryeye.token.client.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdTokenIssuerTest {

	SigningClient signingClient;
	UserDirectoryClient userDirectoryClient;
	IdTokenIssuer issuer;

	@BeforeEach
	void setUp() {
		signingClient = mock(SigningClient.class);
		userDirectoryClient = mock(UserDirectoryClient.class);
		when(signingClient.sign(anyMap(), any())).thenReturn("signed.jwt.value");
		// ProfileClaimMapper 는 mock 이 아니라 실제 구현을 쓴다 (userinfo 와 공유하는 매핑 자체가 검증 대상이다)
		issuer = new IdTokenIssuer(signingClient, userDirectoryClient, new ProfileClaimMapper(),
				"http://localhost:9000", 300);
	}

	private UserProfile profile() {
		return new UserProfile("user-sub-0001", "user", List.of("ROLE_USER"),
				"Star Rye", "starry", "starryeye", "starryeye@example.com", true);
	}

	// OIDC Core 3.1.3.6 예시 벡터: access_token "jHkWEdUXMU1BwAsC4vtUsZwnNvTIxEl0z9K3vx5KF0Y" 의 at_hash 는 "77QmUPtjPfzWtF2AnpK9RQ"
	@Test
	void computesAtHashPerSpec() {
		assertThat(issuer.computeAtHash("jHkWEdUXMU1BwAsC4vtUsZwnNvTIxEl0z9K3vx5KF0Y"))
				.isEqualTo("77QmUPtjPfzWtF2AnpK9RQ");
	}

	@Test
	void includesRequiredClaimsAndNonce() {
		when(userDirectoryClient.getUser("user-sub-0001")).thenReturn(profile());

		issuer.issue("user-sub-0001", "my-client", "openid", "nonce-1", 1700000000L, "access-token-value");

		ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
		verify(signingClient).sign(captor.capture(), any());
		Map<String, Object> claims = captor.getValue();

		assertThat(claims).containsEntry("iss", "http://localhost:9000");
		assertThat(claims).containsEntry("sub", "user-sub-0001");
		assertThat(claims).containsEntry("aud", "my-client");
		assertThat(claims).containsKeys("exp", "iat");
		assertThat(claims).containsEntry("nonce", "nonce-1");
		assertThat(claims).containsEntry("auth_time", 1700000000L);
		assertThat(claims).containsKey("at_hash");
	}

	@Test
	void omitsNonceWhenNotRequested() {
		when(userDirectoryClient.getUser("user-sub-0001")).thenReturn(profile());

		issuer.issue("user-sub-0001", "my-client", "openid", null, 1700000000L, "access-token-value");

		ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
		verify(signingClient).sign(captor.capture(), any());
		assertThat(captor.getValue()).doesNotContainKey("nonce");
	}

	@Test
	void includesProfileClaimsOnlyWhenScopePresent() {
		when(userDirectoryClient.getUser("user-sub-0001")).thenReturn(profile());

		issuer.issue("user-sub-0001", "my-client", "openid profile", null, 1700000000L, "at");

		ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
		verify(signingClient).sign(captor.capture(), any());
		Map<String, Object> claims = captor.getValue();
		assertThat(claims).containsEntry("name", "Star Rye");
		assertThat(claims).containsEntry("nickname", "starry");
		assertThat(claims).containsEntry("preferred_username", "starryeye");
		assertThat(claims).doesNotContainKey("email");
	}

	@Test
	void includesEmailClaimsWhenScopePresent() {
		when(userDirectoryClient.getUser("user-sub-0001")).thenReturn(profile());

		issuer.issue("user-sub-0001", "my-client", "openid email", null, 1700000000L, "at");

		ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
		verify(signingClient).sign(captor.capture(), any());
		Map<String, Object> claims = captor.getValue();
		assertThat(claims).containsEntry("email", "starryeye@example.com");
		assertThat(claims).containsEntry("email_verified", true);
		assertThat(claims).doesNotContainKey("name");
	}

	// 일시 장애(연결 실패·5xx): 사용자 존재 여부가 미확정이므로 인증 주장 자체는 살리고 프로필만 degrade 한다.
	@Test
	void issuesWithoutProfileClaimsWhenUserDirectoryFails() {
		when(userDirectoryClient.getUser("user-sub-0001")).thenThrow(new RuntimeException("user-directory down"));

		String idToken = issuer.issue("user-sub-0001", "my-client", "openid profile", null, 1700000000L, "at");

		assertThat(idToken).isEqualTo("signed.jwt.value");
		ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
		verify(signingClient).sign(captor.capture(), any());
		Map<String, Object> claims = captor.getValue();
		assertThat(claims).containsEntry("sub", "user-sub-0001"); // 필수 claim 은 유지
		assertThat(claims).doesNotContainKey("name");             // 프로필만 degrade
	}

	// 404 는 "사용자가 없다"는 확정된 사실이다. 존재하지 않는 주체에 대한 인증 주장을 서명해 내보낼 수 없으므로
	// degrade 하지 않고 예외를 전파한다 (호출부가 invalid_grant 로 바꾼다).
	@Test
	void propagatesUserNotFoundInsteadOfIssuing() {
		when(userDirectoryClient.getUser("user-sub-0001"))
				.thenThrow(new UserDirectoryClient.UserNotFoundException());

		assertThatThrownBy(() -> issuer.issue("user-sub-0001", "my-client", "openid profile", null, 1700000000L, "at"))
				.isInstanceOf(UserDirectoryClient.UserNotFoundException.class);

		verify(signingClient, never()).sign(anyMap(), any());
	}

	// email 값이 없으면 email_verified 만 홀로 나가지 않는다 (검증 대상 없는 검증 플래그는 해석 불가)
	@Test
	void omitsEmailVerifiedWhenEmailAbsent() {
		when(userDirectoryClient.getUser("user-sub-0001")).thenReturn(
				new UserProfile("user-sub-0001", "user", List.of("ROLE_USER"),
						"Star Rye", "starry", "starryeye", null, true));

		issuer.issue("user-sub-0001", "my-client", "openid email", null, 1700000000L, "at");

		ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
		verify(signingClient).sign(captor.capture(), any());
		assertThat(captor.getValue()).doesNotContainKeys("email", "email_verified");
	}

	// openid 만 요청하면 프로필 claim 이 필요 없으므로 user-directory 를 호출하지 않는다
	@Test
	void doesNotCallUserDirectoryForOpenidOnlyScope() {
		issuer.issue("user-sub-0001", "my-client", "openid", "nonce-1", 1700000000L, "at");

		verify(userDirectoryClient, never()).getUser(anyString());
	}
}
