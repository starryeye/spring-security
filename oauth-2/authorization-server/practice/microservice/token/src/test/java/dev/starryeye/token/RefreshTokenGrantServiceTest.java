package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.RotateResult;
import dev.starryeye.token.client.TokenStateClient;
import dev.starryeye.token.client.UserDirectoryClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
		when(tokenStateClient.rotate("old-refresh", "my-client", null)).thenReturn(rotated("openid offline_access"));
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
		when(tokenStateClient.rotate("old-refresh", "my-client", null)).thenReturn(rotated("openid"));
		when(accessTokenIssuer.issue(any(), any(), any())).thenReturn("new-access");
		when(idTokenIssuer.issue(any(), any(), any(), any(), anyLong(), any())).thenReturn("new-id");

		service.grant(client(), "old-refresh", null);

		verify(idTokenIssuer).issue(eq("user-sub-0001"), eq("my-client"), eq("openid"),
				isNull(), eq(1700000000L), eq("new-access"));
	}

	// 회전 실패 사유는 전부 invalid_grant 로 뭉갠다
	@Test
	void reuseDetectedBecomesInvalidGrant() {
		when(tokenStateClient.rotate("old-refresh", "my-client", null))
				.thenReturn(new RotateResult("REUSE_DETECTED", null, null, 0L, null, 0L));

		GrantResult result = service.grant(client(), "old-refresh", null);

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("invalid_grant");
		verify(accessTokenIssuer, never()).issue(any(), any(), any());
	}

	@Test
	void notFoundBecomesInvalidGrant() {
		when(tokenStateClient.rotate("old-refresh", "my-client", null))
				.thenReturn(new RotateResult("NOT_FOUND", null, null, 0L, null, 0L));

		assertThat(service.grant(client(), "old-refresh", null).error()).isEqualTo("invalid_grant");
	}

	@Test
	void expiredBecomesInvalidGrant() {
		when(tokenStateClient.rotate("old-refresh", "my-client", null))
				.thenReturn(new RotateResult("EXPIRED", null, null, 0L, null, 0L));

		assertThat(service.grant(client(), "old-refresh", null).error()).isEqualTo("invalid_grant");
	}

	@Test
	void revokedBecomesInvalidGrant() {
		when(tokenStateClient.rotate("old-refresh", "my-client", null))
				.thenReturn(new RotateResult("REVOKED", null, null, 0L, null, 0L));

		GrantResult result = service.grant(client(), "old-refresh", null);

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("invalid_grant");
		verify(accessTokenIssuer, never()).issue(any(), any(), any());
	}

	@Test
	void clientMismatchBecomesInvalidGrant() {
		when(tokenStateClient.rotate("old-refresh", "my-client", null))
				.thenReturn(new RotateResult("CLIENT_MISMATCH", null, null, 0L, null, 0L));

		GrantResult result = service.grant(client(), "old-refresh", null);

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("invalid_grant");
		verify(accessTokenIssuer, never()).issue(any(), any(), any());
	}

	// token-state 가 빈 본문을 준 경우(역직렬화 결과 null)는 "이 토큰이 나쁘다" 가 아니라 "하위 서비스가 답을 못 줬다" 다.
	// invalid_grant 로 답하면 token-state 의 장애를 client 의 잘못으로 돌리게 되므로(설계 §7), 대신 예외를 던져
	// OAuth2ExceptionHandler 가 server_error 로 새게 한다.
	@Test
	void nullRotationResultThrowsIllegalStateException() {
		when(tokenStateClient.rotate("old-refresh", "my-client", null)).thenReturn(null);

		assertThatThrownBy(() -> service.grant(client(), "old-refresh", null))
				.isInstanceOf(IllegalStateException.class);

		verify(accessTokenIssuer, never()).issue(any(), any(), any());
	}

	@Test
	void blankRefreshTokenBecomesInvalidRequest() {
		GrantResult result = service.grant(client(), "", null);

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("invalid_request");
		verify(tokenStateClient, never()).rotate(any(), any(), any());
	}

	// RFC 6749 6: 축소 요청은 저장된 scope 의 부분집합만 허용한다
	@Test
	void narrowedScopeAppliesToThisAccessTokenOnly() {
		when(tokenStateClient.rotate("old-refresh", "my-client", "profile"))
				.thenReturn(rotated("openid profile offline_access"));
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

	// RFC 6749 6: 축소 요청이 저장된 grant 를 벗어나면 token-state 가 회전과 같은 트랜잭션에서 SCOPE_EXCEEDED 를 판정한다.
	// 이 경우 token-state 는 어떤 상태도 바꾸지 않았으므로, 이 서비스는 accessTokenIssuer 를 호출하지 않고 그대로
	// invalid_scope 로 내보낸다 -- client 는 같은 refresh token 으로 올바른 scope 를 다시 보낼 수 있다.
	@Test
	void scopeExceededBecomesInvalidScope() {
		when(tokenStateClient.rotate("old-refresh", "my-client", "openid admin"))
				.thenReturn(new RotateResult("SCOPE_EXCEEDED", null, null, 0L, null, 0L));

		GrantResult result = service.grant(client(), "old-refresh", "openid admin");

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("invalid_scope");
		verify(accessTokenIssuer, never()).issue(any(), any(), any());
	}

	// 회전 후 id token 발급 중 사용자가 삭제된 경우: code 교환 경로(TokenEndpointController)와 같은 판단을 한다.
	// 존재하지 않는 주체에 대한 인증 주장을 만들 수 없으므로 grant 자체를 무효로 보고 invalid_grant 로 내보낸다(500 아님).
	@Test
	void userNotFoundDuringIdTokenIssuanceBecomesInvalidGrant() {
		when(tokenStateClient.rotate("old-refresh", "my-client", null)).thenReturn(rotated("openid"));
		when(accessTokenIssuer.issue(any(), any(), any())).thenReturn("new-access");
		when(idTokenIssuer.issue(any(), any(), any(), any(), anyLong(), any()))
				.thenThrow(new UserDirectoryClient.UserNotFoundException());

		GrantResult result = service.grant(client(), "old-refresh", null);

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("invalid_grant");
	}

	@Test
	void clientWithoutRefreshGrantIsRejected() {
		ClientInfo noRefresh = new ClientInfo("my-client", List.of(), List.of("openid"), "{bcrypt}x",
				List.of("authorization_code"));

		GrantResult result = service.grant(noRefresh, "old-refresh", null);

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("unauthorized_client");
		verify(tokenStateClient, never()).rotate(any(), any(), any());
	}
}
