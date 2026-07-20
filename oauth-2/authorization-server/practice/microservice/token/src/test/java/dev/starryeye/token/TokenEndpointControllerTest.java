package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.ClientRegistryClient;
import dev.starryeye.token.client.SigningClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TokenEndpointControllerTest {

	@Autowired MockMvc mockMvc;
	@MockitoBean AuthorizationCodeStore codeStore;
	@MockitoBean ClientRegistryClient clientRegistryClient;
	@MockitoBean SigningClient signingClient;

	private static final String BASIC = "Basic " + java.util.Base64.getEncoder()
			.encodeToString("my-client:secret".getBytes());

	@Test
	void unknownCodeReturnsInvalidGrant() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(codeStore.consume("badcode")).thenReturn(Optional.empty());

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "authorization_code")
						.param("code", "badcode")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "whatever"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_grant"));
	}

	@Test
	void wrongClientSecretReturnsInvalidClient() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		String badBasic = "Basic " + java.util.Base64.getEncoder()
				.encodeToString("my-client:wrong".getBytes());

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", badBasic)
						.param("grant_type", "authorization_code")
						.param("code", "x")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "v"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_client"));
	}

	private ClientInfo clientInfo() {
		// secret "secret" 의 bcrypt 해시
		String hash = org.springframework.security.crypto.factory.PasswordEncoderFactories
				.createDelegatingPasswordEncoder().encode("secret");
		return new ClientInfo("my-client",
				List.of("http://127.0.0.1:8080/callback"),
				List.of("openid", "profile"), hash, List.of("authorization_code"));
	}
}
