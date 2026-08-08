package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.RotateResult;
import dev.starryeye.token.client.TokenStateClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenGrantServiceSidTest {

	/**
	 * refresh 로 재발급하는 id token 에도 sid 를 싣는다. RP 가 나중에 back-channel logout 을 받았을 때
	 *      자기 세션과 대조할 수 있어야 하기 때문이다.
	 *
	 * 주의. 회전 응답의 sid 를 IdTokenIssuer 로 실제로 넘기는지를 ArgumentCaptor 로 본다. 양쪽을 다
	 *      목으로 두고 "호출됐다" 만 보면 값이 null 로 흘러도 통과한다.
	 */

	@Test
	@DisplayName("회전 응답의 sid 가 id token 발급에 그대로 전달된다")
	void refreshCarriesSidIntoIdToken() {
		TokenStateClient tokenStateClient = mock(TokenStateClient.class);
		AccessTokenIssuer accessTokenIssuer = mock(AccessTokenIssuer.class);
		IdTokenIssuer idTokenIssuer = mock(IdTokenIssuer.class);

		when(tokenStateClient.rotate(anyString(), anyString(), any()))
				.thenReturn(new RotateResult("ROTATED", "user-sub-0001", "openid offline_access",
						1000L, "new-refresh", 9999L, "SID-A"));
		when(accessTokenIssuer.issue(anyString(), anyString(), anyString())).thenReturn("access-jwt");
		when(idTokenIssuer.issue(anyString(), anyString(), anyString(), any(), anyLong(), anyString(), any()))
				.thenReturn("id-jwt");

		RefreshTokenGrantService service = new RefreshTokenGrantService(
				tokenStateClient, accessTokenIssuer, idTokenIssuer, 300L);

		// 실제 ClientInfo 는 (clientId, redirectUris, scopes, clientSecretHash, grantTypes, clientScopes) 6 필드다.
		// 브리프 초안은 8 개 인자를 가정했는데(생성 시점 추정), 실제 레코드에 맞춰 6 개로 고쳤다.
		ClientInfo client = new ClientInfo("my-client", List.of(), List.of("openid", "offline_access"),
				"{bcrypt}x", List.of("refresh_token"), List.of());

		service.grant(client, "old-refresh", null);

		ArgumentCaptor<String> sidCaptor = ArgumentCaptor.forClass(String.class);
		verify(idTokenIssuer).issue(eq("user-sub-0001"), eq("my-client"), anyString(),
				any(), anyLong(), anyString(), sidCaptor.capture());
		assertThat(sidCaptor.getValue()).isEqualTo("SID-A");
	}
}
