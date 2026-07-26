package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.RotateResult;
import dev.starryeye.token.client.TokenStateClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenGrantServiceTest {

	private final TokenStateClient tokenStateClient = mock(TokenStateClient.class);
	private final AccessTokenIssuer accessTokenIssuer = mock(AccessTokenIssuer.class);
	private final IdTokenIssuer idTokenIssuer = mock(IdTokenIssuer.class);

	private final RefreshTokenGrantService service =
			new RefreshTokenGrantService(tokenStateClient, accessTokenIssuer, idTokenIssuer, 300L);

	private ClientInfo client() {
		return new ClientInfo("my-client", List.of("http://127.0.0.1:8080/callback"),
				List.of("openid", "profile", "offline_access"), "{bcrypt}x",
				List.of("authorization_code", "refresh_token"));
	}

	private RotateResult rotated(String scope) {
		return new RotateResult("ROTATED", "user-sub-0001", scope, 1700000000L, "new-refresh", 1800000000L);
	}

	@Test
	void rotatedGrantReturnsNewAccessAndRefreshToken() {
		when(tokenStateClient.rotate("old-refresh", "my-client")).thenReturn(rotated("openid offline_access"));
		when(accessTokenIssuer.issue(any(), any(), any())).thenReturn("new-access");
		when(idTokenIssuer.issue(any(), any(), any(), any(), anyLong(), any())).thenReturn("new-id");

		GrantResult result = service.grant(client(), "old-refresh", null);

		assertThat(result.success()).isTrue();
		assertThat(result.response().access_token()).isEqualTo("new-access");
		assertThat(result.response().refresh_token()).isEqualTo("new-refresh");
		assertThat(result.response().id_token()).isEqualTo("new-id");
		assertThat(result.response().scope()).isEqualTo("openid offline_access");
	}

	// OIDC Core 12.2: refresh 로 낸 id token 에는 nonce 를 넣지 않고 auth_time 은 원래 인증 시각을 유지한다
	@Test
	void refreshedIdTokenHasNoNonceAndKeepsOriginalAuthTime() {
		when(tokenStateClient.rotate("old-refresh", "my-client")).thenReturn(rotated("openid"));
		when(accessTokenIssuer.issue(any(), any(), any())).thenReturn("new-access");
		when(idTokenIssuer.issue(any(), any(), any(), any(), anyLong(), any())).thenReturn("new-id");

		service.grant(client(), "old-refresh", null);

		verify(idTokenIssuer).issue(eq("user-sub-0001"), eq("my-client"), eq("openid"),
				isNull(), eq(1700000000L), eq("new-access"));
	}

	// 회전 실패 사유는 전부 invalid_grant 로 뭉갠다
	@Test
	void reuseDetectedBecomesInvalidGrant() {
		when(tokenStateClient.rotate("old-refresh", "my-client"))
				.thenReturn(new RotateResult("REUSE_DETECTED", null, null, 0L, null, 0L));

		GrantResult result = service.grant(client(), "old-refresh", null);

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("invalid_grant");
		verify(accessTokenIssuer, never()).issue(any(), any(), any());
	}

	@Test
	void notFoundBecomesInvalidGrant() {
		when(tokenStateClient.rotate("old-refresh", "my-client"))
				.thenReturn(new RotateResult("NOT_FOUND", null, null, 0L, null, 0L));

		assertThat(service.grant(client(), "old-refresh", null).error()).isEqualTo("invalid_grant");
	}

	@Test
	void expiredBecomesInvalidGrant() {
		when(tokenStateClient.rotate("old-refresh", "my-client"))
				.thenReturn(new RotateResult("EXPIRED", null, null, 0L, null, 0L));

		assertThat(service.grant(client(), "old-refresh", null).error()).isEqualTo("invalid_grant");
	}

	// RFC 6749 6: 축소 요청은 저장된 scope 의 부분집합만 허용한다
	@Test
	void narrowedScopeAppliesToThisAccessTokenOnly() {
		when(tokenStateClient.rotate("old-refresh", "my-client")).thenReturn(rotated("openid profile offline_access"));
		when(accessTokenIssuer.issue(any(), any(), any())).thenReturn("new-access");

		GrantResult result = service.grant(client(), "old-refresh", "profile");

		assertThat(result.success()).isTrue();
		assertThat(result.response().scope()).isEqualTo("profile");
		ArgumentCaptor<String> scopeCaptor = ArgumentCaptor.forClass(String.class);
		verify(accessTokenIssuer).issue(any(), any(), scopeCaptor.capture());
		assertThat(scopeCaptor.getValue()).isEqualTo("profile");
		// openid 를 뺐으므로 이번 응답에는 id token 이 없다
		assertThat(result.response().id_token()).isNull();
	}

	@Test
	void scopeBeyondStoredScopeIsRejected() {
		when(tokenStateClient.rotate("old-refresh", "my-client")).thenReturn(rotated("openid"));

		GrantResult result = service.grant(client(), "old-refresh", "openid admin");

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("invalid_scope");
		verify(accessTokenIssuer, never()).issue(any(), any(), any());
	}

	@Test
	void clientWithoutRefreshGrantIsRejected() {
		ClientInfo noRefresh = new ClientInfo("my-client", List.of(), List.of("openid"), "{bcrypt}x",
				List.of("authorization_code"));

		GrantResult result = service.grant(noRefresh, "old-refresh", null);

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("unauthorized_client");
		verify(tokenStateClient, never()).rotate(any(), any());
	}
}
