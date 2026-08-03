package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.ClientRegistryClient;
import dev.starryeye.token.client.IssuedRefreshToken;
import dev.starryeye.token.client.SigningClient;
import dev.starryeye.token.client.TokenStateClient;
import dev.starryeye.token.dto.TokenResponse;
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
import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
	@MockitoBean TokenStateClient tokenStateClient;
	@MockitoBean RefreshTokenGrantService refreshTokenGrantService;
	@MockitoBean ClientCredentialsGrantService clientCredentialsGrantService;

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
				new AuthorizationCodeData("other-client", "http://127.0.0.1:8080/callback", "openid", "user-sub-0001", "chal", null, 1700000000L, null)));

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
						"E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", null, 1700000000L, null)));

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
						"E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", "nonce-abc", 1700000000L, null)));
		when(signingClient.sign(any(), any())).thenReturn("signed-access-token");
		when(idTokenIssuer.issue(any(), any(), any(), any(), anyLong(), any(), any())).thenReturn("signed-id-token");

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
				nonceCaptor.capture(), authTimeCaptor.capture(), accessTokenCaptor.capture(), any());

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
						"E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", null, 1700000000L, null)));
		when(signingClient.sign(any(), any())).thenReturn("signed-access-token");

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "authorization_code")
						.param("code", "code123")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.access_token").exists())
				.andExpect(jsonPath("$.id_token").doesNotExist());

		verify(idTokenIssuer, never()).issue(any(), any(), any(), any(), anyLong(), any(), any());
	}

	// code 발급 후 사용자가 삭제된 경우: id token 을 만들 수 없으므로 grant 를 무효로 본다 (500 도 200 도 아니다)
	@Test
	void deletedUserReturnsInvalidGrant() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(codeStore.consume("code123")).thenReturn(java.util.Optional.of(
				new AuthorizationCodeData("my-client", "http://127.0.0.1:8080/callback", "openid profile", "user-sub-0001",
						"E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", null, 1700000000L, null)));
		when(signingClient.sign(any(), any())).thenReturn("signed-access-token");
		when(idTokenIssuer.issue(any(), any(), any(), any(), anyLong(), any(), any()))
				.thenThrow(new dev.starryeye.token.client.UserDirectoryClient.UserNotFoundException());

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "authorization_code")
						.param("code", "code123")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_grant"));
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
				.andExpect(jsonPath("$.introspection_endpoint").value(issuer + "/oauth2/introspect"))
				.andExpect(jsonPath("$.revocation_endpoint").value(issuer + "/oauth2/revoke"))
				.andExpect(jsonPath("$.response_types_supported[0]").value("code"))
				.andExpect(jsonPath("$.response_types_supported.length()").value(1))
				.andExpect(jsonPath("$.grant_types_supported[0]").value("authorization_code"))
				.andExpect(jsonPath("$.grant_types_supported[1]").value("refresh_token"))
				.andExpect(jsonPath("$.grant_types_supported[2]").value("client_credentials"))
				.andExpect(jsonPath("$.grant_types_supported.length()").value(3))
				.andExpect(jsonPath("$.code_challenge_methods_supported[0]").value("S256"))
				.andExpect(jsonPath("$.code_challenge_methods_supported.length()").value(1))
				.andExpect(jsonPath("$.subject_types_supported[0]").value("public"))
				.andExpect(jsonPath("$.subject_types_supported.length()").value(1))
				.andExpect(jsonPath("$.id_token_signing_alg_values_supported[0]").value("RS256"))
				.andExpect(jsonPath("$.id_token_signing_alg_values_supported.length()").value(1))
				.andExpect(jsonPath("$.introspection_endpoint_auth_methods_supported").doesNotExist())
				.andExpect(jsonPath("$.revocation_endpoint_auth_methods_supported[0]").value("client_secret_basic"))
				.andExpect(jsonPath("$.revocation_endpoint_auth_methods_supported.length()").value(1))
				.andExpect(jsonPath("$.scopes_supported[0]").value("openid"))
				.andExpect(jsonPath("$.scopes_supported[1]").value("profile"))
				.andExpect(jsonPath("$.scopes_supported[2]").value("email"))
				.andExpect(jsonPath("$.scopes_supported[3]").value("offline_access"))
				.andExpect(jsonPath("$.scopes_supported[4]").value("introspect"))
				.andExpect(jsonPath("$.scopes_supported.length()").value(5))
				// at_hash 는 id token 에 항상 실리므로 광고 목록에도 있어야 한다
				.andExpect(jsonPath("$.claims_supported", hasItem("at_hash")));
	}

	// discovery 의 scopes_supported 와 client_credentials 거부 집합의 관계를 고정한다.
	// 두 목록은 서로 다른 파일의 별개 리터럴이라 결합이 코드로 강제되지 않는다 — 사용자 위임 scope 를
	// discovery 에만 추가하면 그 scope 가 client_credentials 로 새어나간다. 이 테스트가 그 이탈을 잡는다.
	@Test
	void userDelegatedScopeSetCoversEveryAdvertisedScopeExceptIntrospect() throws Exception {
		String body = mockMvc.perform(get("/.well-known/openid-configuration"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();

		List<String> advertised = com.jayway.jsonpath.JsonPath.read(body, "$.scopes_supported");

		// introspect 는 client 능력 scope 라 유일하게 거부 집합 밖이다.
		assertThat(advertised).contains("introspect");
		assertThat(advertised.stream().filter(scope -> !scope.equals("introspect")).toList())
				.containsExactlyInAnyOrderElementsOf(ClientCredentialsGrantService.USER_DELEGATED_SCOPES);
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

	// offline_access 동의 + refresh_token grant 등록, 둘 다 있어야 refresh token 이 나온다
	@Test
	void offlineAccessScopeIssuesRefreshToken() throws Exception {
		when(codeStore.consume("code-1")).thenReturn(Optional.of(new AuthorizationCodeData(
				"my-client", "http://127.0.0.1:8080/callback", "openid offline_access", "user-sub-0001",
				"E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", "nonce-1", 1700000000L, null)));
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(signingClient.sign(any(), any())).thenReturn("signed-access-token");
		when(idTokenIssuer.issue(any(), any(), any(), any(), anyLong(), any(), any())).thenReturn("signed-id-token");
		when(tokenStateClient.issue(eq("my-client"), eq("user-sub-0001"), eq("openid offline_access"), eq(1700000000L)))
				.thenReturn(new IssuedRefreshToken("refresh-token-1", 1800000000L, "family-1"));

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "authorization_code")
						.param("code", "code-1")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.refresh_token").value("refresh-token-1"));
	}

	// 동의하지 않았으면 발급하지 않는다. token-state 를 부르지도 않는다.
	@Test
	void withoutOfflineAccessScopeNoRefreshTokenIsIssued() throws Exception {
		when(codeStore.consume("code-1")).thenReturn(Optional.of(new AuthorizationCodeData(
				"my-client", "http://127.0.0.1:8080/callback", "openid profile", "user-sub-0001",
				"E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", "nonce-1", 1700000000L, null)));
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(signingClient.sign(any(), any())).thenReturn("signed-access-token");
		when(idTokenIssuer.issue(any(), any(), any(), any(), anyLong(), any(), any())).thenReturn("signed-id-token");

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "authorization_code")
						.param("code", "code-1")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.refresh_token").doesNotExist());

		verify(tokenStateClient, never()).issue(any(), any(), any(), anyLong());
	}

	// offline_access 는 동의됐지만 client 의 grantTypes 에 refresh_token 이 없으면 발급하지 않는다.
	// 관문 하나만으로는 부족하다 — 사용자 동의와 client 등록 능력은 서로 다른 질문이라 둘 다 참이어야 한다.
	@Test
	void withoutRefreshTokenGrantNoRefreshTokenIsIssuedEvenWithOfflineAccessScope() throws Exception {
		when(codeStore.consume("code-1")).thenReturn(Optional.of(new AuthorizationCodeData(
				"my-client", "http://127.0.0.1:8080/callback", "openid offline_access", "user-sub-0001",
				"E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", "nonce-1", 1700000000L, null)));
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfoWithoutRefreshTokenGrant());
		when(signingClient.sign(any(), any())).thenReturn("signed-access-token");
		when(idTokenIssuer.issue(any(), any(), any(), any(), anyLong(), any(), any())).thenReturn("signed-id-token");

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "authorization_code")
						.param("code", "code-1")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.access_token").exists())
				.andExpect(jsonPath("$.refresh_token").doesNotExist());

		verify(tokenStateClient, never()).issue(any(), any(), any(), anyLong());
	}

	// token-state 발급 호출이 실패하면 access token 만 내려주고 refresh token 을 조용히 빠뜨릴 수 없다 -- client 는
	// offline_access 에 동의했다고 믿지만 실제로는 재로그인 없이 재발급받을 수단이 없는 상태가 된다.
	// 발급 · 회전 · 폐기 세 fail-closed 경로 중 발급만 회귀 테스트가 없었다.
	@Test
	void tokenStateIssueFailureReturns500NotPartialSuccess() throws Exception {
		when(codeStore.consume("code-1")).thenReturn(Optional.of(new AuthorizationCodeData(
				"my-client", "http://127.0.0.1:8080/callback", "openid offline_access", "user-sub-0001",
				"E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", "nonce-1", 1700000000L, null)));
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(signingClient.sign(any(), any())).thenReturn("signed-access-token");
		when(idTokenIssuer.issue(any(), any(), any(), any(), anyLong(), any(), any())).thenReturn("signed-id-token");
		when(tokenStateClient.issue(any(), any(), any(), anyLong()))
				.thenThrow(new IllegalStateException("token-state is down"));

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "authorization_code")
						.param("code", "code-1")
						.param("redirect_uri", "http://127.0.0.1:8080/callback")
						.param("code_verifier", "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("server_error"))
				.andExpect(jsonPath("$.access_token").doesNotExist());
	}

	@Test
	void refreshGrantDelegatesToRefreshTokenGrantService() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(refreshTokenGrantService.grant(any(), eq("old-refresh"), isNull()))
				.thenReturn(GrantResult.ok(new TokenResponse("new-access", "Bearer", 300L,
						"openid", null, "new-refresh")));

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "refresh_token")
						.param("refresh_token", "old-refresh"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.access_token").value("new-access"))
				.andExpect(jsonPath("$.refresh_token").value("new-refresh"));
	}

	@Test
	void refreshGrantFailureBecomesOAuth2Error() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(refreshTokenGrantService.grant(any(), any(), any()))
				.thenReturn(GrantResult.failed("invalid_grant", "refresh token is not valid"));

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "refresh_token")
						.param("refresh_token", "old-refresh"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_grant"));
	}

	// client-registry 가 준 clientScopes 가 grant 판정에 실제로 도달하는지를 확인한다. eq()/any() 만으로는
	// 컨트롤러가 client-registry 응답 대신 빈 ClientInfo 를 넘겨도 스위트가 초록일 수 있으므로,
	// ArgumentCaptor 로 실제로 넘어간 ClientInfo 를 잡아 client-registry stub 이 준 clientScopes 와 비교한다.
	@Test
	void clientCredentialsGrantDelegatesToItsServiceWithClientRegistryClientScopes() throws Exception {
		ClientInfo clientWithClientScopes = new ClientInfo("my-client",
				List.of("http://127.0.0.1:8080/callback"),
				List.of("openid", "profile"),
				clientInfo().clientSecretHash(),
				List.of("client_credentials"),
				List.of("introspect", "audit"));
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientWithClientScopes);
		when(clientCredentialsGrantService.grant(any(), eq("introspect")))
				.thenReturn(GrantResult.ok(new TokenResponse("cc-token", "Bearer", 300L,
						"introspect", null, null)));

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "client_credentials")
						.param("scope", "introspect"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.access_token").value("cc-token"))
				.andExpect(jsonPath("$.refresh_token").doesNotExist())
				.andExpect(jsonPath("$.id_token").doesNotExist());

		ArgumentCaptor<ClientInfo> clientCaptor = ArgumentCaptor.forClass(ClientInfo.class);
		verify(clientCredentialsGrantService).grant(clientCaptor.capture(), eq("introspect"));
		assertThat(clientCaptor.getValue().clientScopes()).isEqualTo(List.of("introspect", "audit"));
	}

	@Test
	void clientCredentialsFailureBecomesOAuth2Error() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(clientCredentialsGrantService.grant(any(), any()))
				.thenReturn(GrantResult.failed("unauthorized_client", "client not authorized for client_credentials grant"));

		mockMvc.perform(post("/oauth2/token")
						.header("Authorization", BASIC)
						.param("grant_type", "client_credentials"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("unauthorized_client"));
	}

	// 인증 없는 요청은 grant type 을 알아내기 전에 막힌다
	@Test
	void unsupportedGrantTypeWithoutCredentialsIsInvalidClient() throws Exception {
		mockMvc.perform(post("/oauth2/token").param("grant_type", "password"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_client"));
	}

	private ClientInfo clientInfo() {
		// secret "secret" 의 bcrypt 해시
		String hash = org.springframework.security.crypto.factory.PasswordEncoderFactories
				.createDelegatingPasswordEncoder().encode("secret");
		return new ClientInfo("my-client",
				List.of("http://127.0.0.1:8080/callback"),
				List.of("openid", "profile"), hash, List.of("authorization_code", "refresh_token"), List.of());
	}

	private ClientInfo clientInfoWithoutAuthorizationCodeGrant() {
		String hash = org.springframework.security.crypto.factory.PasswordEncoderFactories
				.createDelegatingPasswordEncoder().encode("secret");
		return new ClientInfo("my-client",
				List.of("http://127.0.0.1:8080/callback"),
				List.of("openid", "profile"), hash, List.of("client_credentials"), List.of());
	}

	private ClientInfo clientInfoWithoutRefreshTokenGrant() {
		// authorization_code 는 등록됐지만 refresh_token 은 등록되지 않은 client. secret "secret" 의 bcrypt 해시.
		String hash = org.springframework.security.crypto.factory.PasswordEncoderFactories
				.createDelegatingPasswordEncoder().encode("secret");
		return new ClientInfo("my-client",
				List.of("http://127.0.0.1:8080/callback"),
				List.of("openid", "profile", "offline_access"), hash, List.of("authorization_code"), List.of());
	}
}
