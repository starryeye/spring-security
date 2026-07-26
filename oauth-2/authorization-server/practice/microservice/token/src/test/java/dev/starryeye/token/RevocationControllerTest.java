package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.ClientRegistryClient;
import dev.starryeye.token.client.SigningClient;
import dev.starryeye.token.client.TokenStateClient;
import dev.starryeye.token.client.UserDirectoryClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RevocationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ClientRegistryClient clientRegistryClient;

	@MockitoBean
	private TokenStateClient tokenStateClient;

	@MockitoBean
	private AccessTokenVerifier accessTokenVerifier;

	@MockitoBean
	private SigningClient signingClient;

	@MockitoBean
	private UserDirectoryClient userDirectoryClient;

	@MockitoBean
	private AuthorizationCodeStore codeStore;

	private static final String BASIC = "Basic " + Base64.getEncoder()
			.encodeToString("my-client:secret".getBytes(StandardCharsets.UTF_8));

	private ClientInfo clientInfo() {
		String hash = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("secret");
		return new ClientInfo("my-client", List.of("http://127.0.0.1:8080/callback"),
				List.of("openid"), hash, List.of("authorization_code", "refresh_token"));
	}

	@Test
	void revokingOwnRefreshTokenReturns200() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(tokenStateClient.revoke("refresh-1", "my-client")).thenReturn(true);

		mockMvc.perform(post("/oauth2/revoke")
						.header("Authorization", BASIC)
						.param("token", "refresh-1"))
				.andExpect(status().isOk());
	}

	// RFC 7009 2.2: 존재하지 않는 토큰에도 200 이다. 오류로 갈라 주면 토큰 존재 여부를 탐색할 수 있다.
	@Test
	void revokingUnknownTokenAlsoReturns200() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(tokenStateClient.revoke("ghost", "my-client")).thenReturn(false);

		mockMvc.perform(post("/oauth2/revoke")
						.header("Authorization", BASIC)
						.param("token", "ghost"))
				.andExpect(status().isOk());
	}

	// 이 서버는 access token 을 폐기하지 않는다 (RFC 7009 2 는 access token 폐기를 MAY 로 둔다)
	@Test
	void accessTokenHintIsAcceptedWithoutRevoking() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());

		mockMvc.perform(post("/oauth2/revoke")
						.header("Authorization", BASIC)
						.param("token", "some-jwt")
						.param("token_type_hint", "access_token"))
				.andExpect(status().isOk());

		verify(tokenStateClient, never()).revoke(any(), any());
	}

	@Test
	void missingCredentialsReturnsInvalidClient() throws Exception {
		mockMvc.perform(post("/oauth2/revoke").param("token", "refresh-1"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_client"));

		verify(tokenStateClient, never()).revoke(any(), any());
	}

	@Test
	void missingTokenParameterReturnsInvalidRequest() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());

		mockMvc.perform(post("/oauth2/revoke").header("Authorization", BASIC))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_request"));
	}
}
