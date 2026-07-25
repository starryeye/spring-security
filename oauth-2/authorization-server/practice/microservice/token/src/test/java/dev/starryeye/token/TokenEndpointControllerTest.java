package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.ClientRegistryClient;
import dev.starryeye.token.client.SigningClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TokenEndpointControllerTest {

	@Autowired MockMvc mockMvc;
	@MockitoBean AuthorizationCodeStore codeStore;
	@MockitoBean ClientRegistryClient clientRegistryClient;
	@MockitoBean SigningClient signingClient;
	@MockitoBean IdTokenIssuer idTokenIssuer;

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
				new AuthorizationCodeData("other-client", "http://127.0.0.1:8080/callback", "openid", "user-sub-0001", "chal", null, 1700000000L)));

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
						"E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", null, 1700000000L)));

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "authorization_code")
						.param("code", "code123")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "the-wrong-verifier"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_grant"));
	}

	// 등록된 grantTypes 에 authorization_code 가 없으면 client 인증 성공 이후에도 unauthorized_client (Finding #5)
	@Test
	void grantTypeNotAuthorizedReturnsUnauthorizedClient() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfoWithoutAuthorizationCodeGrant());

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "authorization_code")
						.param("code", "x")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "v"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("unauthorized_client"));
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

	// 성공 경로: openid scope 요청 시 access_token 과 함께 id_token 이 발급되고, idTokenIssuer 가 올바른 인자로 호출된다
	// PKCE 는 wrongCodeVerifierReturnsInvalidGrant 와 동일한 RFC 7636 부록 B 벡터를 재사용한다.
	@Test
	void openidScopeIssuesIdToken() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(codeStore.consume("code123")).thenReturn(java.util.Optional.of(
				new AuthorizationCodeData("my-client", "http://127.0.0.1:8080/callback", "openid profile", "user-sub-0001",
						"E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", "nonce-abc", 1700000000L)));
		when(signingClient.sign(any())).thenReturn("signed-access-token");
		when(idTokenIssuer.issue(any(), any(), any(), any(), anyLong(), any())).thenReturn("signed-id-token");

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "authorization_code")
						.param("code", "code123")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.access_token").exists())
				.andExpect(jsonPath("$.id_token").exists());

		ArgumentCaptor<String> subCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> clientIdCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> scopeCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> nonceCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Long> authTimeCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<String> accessTokenCaptor = ArgumentCaptor.forClass(String.class);
		verify(idTokenIssuer).issue(subCaptor.capture(), clientIdCaptor.capture(), scopeCaptor.capture(),
				nonceCaptor.capture(), authTimeCaptor.capture(), accessTokenCaptor.capture());

		assertThat(subCaptor.getValue()).isEqualTo("user-sub-0001");
		assertThat(clientIdCaptor.getValue()).isEqualTo("my-client");
		assertThat(scopeCaptor.getValue()).isEqualTo("openid profile");
		assertThat(nonceCaptor.getValue()).isEqualTo("nonce-abc");
		assertThat(authTimeCaptor.getValue()).isEqualTo(1700000000L);
		// at_hash 는 access token 으로 계산돼야 하므로, signingClient.sign(...) 이 방금 반환한 access token 과 같아야 한다
		assertThat(accessTokenCaptor.getValue()).isEqualTo("signed-access-token");
	}

	// 성공 경로: openid 가 없는 scope 는 id_token 을 발급하지 않고 idTokenIssuer 를 호출하지도 않는다
	@Test
	void nonOpenidScopeOmitsIdToken() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(codeStore.consume("code123")).thenReturn(java.util.Optional.of(
				new AuthorizationCodeData("my-client", "http://127.0.0.1:8080/callback", "profile", "user-sub-0001",
						"E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", null, 1700000000L)));
		when(signingClient.sign(any())).thenReturn("signed-access-token");

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "authorization_code")
						.param("code", "code123")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.access_token").exists())
				.andExpect(jsonPath("$.id_token").doesNotExist());

		verify(idTokenIssuer, never()).issue(any(), any(), any(), any(), anyLong(), any());
	}

	// discovery metadata: openid-configuration 엔드포인트가 구현된 capability 들을 정확히 광고하는지 검증
	@Test
	void openidConfigurationAdvertisesImplementedCapabilities() throws Exception {
		String issuer = "http://localhost:9000";

		mockMvc.perform(get("/.well-known/openid-configuration"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.issuer").value(issuer))
				.andExpect(jsonPath("$.authorization_endpoint").value(issuer + "/oauth2/authorize"))
				.andExpect(jsonPath("$.token_endpoint").value(issuer + "/oauth2/token"))
				.andExpect(jsonPath("$.jwks_uri").value(issuer + "/oauth2/jwks"))
				.andExpect(jsonPath("$.userinfo_endpoint").value(issuer + "/userinfo"))
				.andExpect(jsonPath("$.response_types_supported[0]").value("code"))
				.andExpect(jsonPath("$.grant_types_supported[0]").value("authorization_code"))
				.andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"))
				.andExpect(jsonPath("$.subject_types_supported[0]").value("public"))
				.andExpect(jsonPath("$.id_token_signing_alg_values_supported[0]").value("RS256"))
				.andExpect(jsonPath("$.scopes_supported[0]").value("openid"))
				.andExpect(jsonPath("$.scopes_supported[1]").value("profile"))
				.andExpect(jsonPath("$.scopes_supported[2]").value("email"))
				.andExpect(jsonPath("$.scopes_supported.length()").value(3));
	}

	// discovery metadata: 두 표준 경로 (oauth-authorization-server, openid-configuration) 가 동일한 문서를 서빙하는지 검증
	@Test
	void bothDiscoveryPathsReturnIdenticalDocument() throws Exception {
		String oauth2Path = mockMvc.perform(get("/.well-known/oauth-authorization-server"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		String openidPath = mockMvc.perform(get("/.well-known/openid-configuration"))
				.andExpect(status().isOk())
				.andReturn()
				.getResponse()
				.getContentAsString();

		assertThat(oauth2Path).isEqualTo(openidPath);
	}

	private ClientInfo clientInfo() {
		// secret "secret" 의 bcrypt 해시
		String hash = org.springframework.security.crypto.factory.PasswordEncoderFactories
				.createDelegatingPasswordEncoder().encode("secret");
		return new ClientInfo("my-client",
				List.of("http://127.0.0.1:8080/callback"),
				List.of("openid", "profile"), hash, List.of("authorization_code"));
	}

	private ClientInfo clientInfoWithoutAuthorizationCodeGrant() {
		String hash = org.springframework.security.crypto.factory.PasswordEncoderFactories
				.createDelegatingPasswordEncoder().encode("secret");
		return new ClientInfo("my-client",
				List.of("http://127.0.0.1:8080/callback"),
				List.of("openid", "profile"), hash, List.of("client_credentials"));
	}
}
