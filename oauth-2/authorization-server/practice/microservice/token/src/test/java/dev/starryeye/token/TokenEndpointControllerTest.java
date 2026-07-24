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

	// code 바인딩: 다른 client 명의로 발급된 code 도용 차단 (code injection 방어)
	@Test
	void codeBoundToDifferentClientReturnsInvalidGrant() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(codeStore.consume("code123")).thenReturn(java.util.Optional.of(
				new AuthorizationCodeData("other-client", "http://127.0.0.1:8080/callback", "openid", "user-sub-0001", "chal")));

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "authorization_code")
						.param("code", "code123")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "whatever"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_grant"));
	}

	// PKCE 실패: 저장된 challenge 와 안 맞는 verifier 는 거부 (실 PkceValidator 사용)
	// RFC 7636 부록 B 벡터: challenge E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM 는 verifier dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk 에 대응. 다른 verifier 를 보낸다.
	@Test
	void wrongCodeVerifierReturnsInvalidGrant() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(codeStore.consume("code123")).thenReturn(java.util.Optional.of(
				new AuthorizationCodeData("my-client", "http://127.0.0.1:8080/callback", "openid", "user-sub-0001",
						"E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM")));

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "authorization_code")
						.param("code", "code123")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "the-wrong-verifier"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_grant"));
	}

	// 잘못된 base64 Authorization 헤더 -> 500 이 아니라 invalid_client (Fix 1a 회귀)
	@Test
	void malformedBasicHeaderReturnsInvalidClient() throws Exception {
		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", "Basic $$$not-base64$$$")
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
