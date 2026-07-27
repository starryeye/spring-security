package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.ClientRegistryClient;
import dev.starryeye.token.client.RefreshTokenInfo;
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
class IntrospectionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ClientRegistryClient clientRegistryClient;

	@MockitoBean
	private AccessTokenVerifier accessTokenVerifier;

	@MockitoBean
	private TokenStateClient tokenStateClient;

	@MockitoBean
	private SigningClient signingClient;

	@MockitoBean
	private UserDirectoryClient userDirectoryClient;

	@MockitoBean
	private AuthorizationCodeStore codeStore;

	private static final String BASIC = "Basic " + Base64.getEncoder()
			.encodeToString("article-api:secret".getBytes(StandardCharsets.UTF_8));

	private ClientInfo articleApi() {
		String hash = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("secret");
		return new ClientInfo("article-api", List.of(), List.of(), hash, List.of());
	}

	@Test
	void accessTokenIsIntrospectedLocally() throws Exception {
		when(clientRegistryClient.getClient("article-api")).thenReturn(articleApi());
		when(accessTokenVerifier.verify("access-token")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("user-sub-0001", List.of("openid", "profile"),
						"my-client", 1800000000L, 1700000000L));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", BASIC)
						.param("token", "access-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.sub").value("user-sub-0001"))
				.andExpect(jsonPath("$.client_id").value("my-client"))
				.andExpect(jsonPath("$.scope").value("openid profile"))
				.andExpect(jsonPath("$.token_type").value("Bearer"));

		// access token 은 폐기 대상이 아니므로 token-state 를 조회하지 않는다
		verify(tokenStateClient, never()).introspect(any());
	}

	@Test
	void nonJwtTokenFallsBackToTokenState() throws Exception {
		when(clientRegistryClient.getClient("article-api")).thenReturn(articleApi());
		when(accessTokenVerifier.verify("opaque-refresh"))
				.thenThrow(new AccessTokenVerifier.InvalidTokenException("malformed token"));
		when(tokenStateClient.introspect("opaque-refresh")).thenReturn(
				new RefreshTokenInfo(true, "user-sub-0001", "my-client", "openid offline_access",
						1800000000L, 1700000000L));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", BASIC)
						.param("token", "opaque-refresh"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.sub").value("user-sub-0001"))
				.andExpect(jsonPath("$.scope").value("openid offline_access"))
				.andExpect(jsonPath("$.token_type").doesNotExist()); // refresh 에는 token_type 이 없다
	}

	// jwks 조회 실패(signing 장애)는 토큰의 죄가 아니다. InvalidTokenException 이 아닌 예외이므로
	// token-state 로 새지도, {"active": false} 로 둔갑하지도 않고 500 server_error 여야 한다.
	@Test
	void signingFailureReturns500NotInactive() throws Exception {
		when(clientRegistryClient.getClient("article-api")).thenReturn(articleApi());
		when(accessTokenVerifier.verify("tok")).thenThrow(new IllegalStateException("signing is down"));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", BASIC)
						.param("token", "tok"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("server_error"));

		verify(tokenStateClient, never()).introspect(any());
	}

	// token-state 가 빈 본문을 준 경우(역직렬화 결과 null)는 "비활성" 이 아니라 "확인하지 못했다" 다.
	// {"active": false} 로 내보내면 살아있는 토큰을 죽었다고 말하는 셈이라 resource server 가 멀쩡한 요청을 거절한다.
	@Test
	void nullIntrospectionResultReturns500NotInactive() throws Exception {
		when(clientRegistryClient.getClient("article-api")).thenReturn(articleApi());
		when(accessTokenVerifier.verify("opaque-refresh"))
				.thenThrow(new AccessTokenVerifier.InvalidTokenException("malformed token"));
		when(tokenStateClient.introspect("opaque-refresh")).thenReturn(null);

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", BASIC)
						.param("token", "opaque-refresh"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("server_error"));
	}

	// 비활성 응답에서는 어떤 정보도 새지 않아야 한다 (RFC 7662 2.2)
	@Test
	void inactiveResponseContainsOnlyActiveFalse() throws Exception {
		when(clientRegistryClient.getClient("article-api")).thenReturn(articleApi());
		when(accessTokenVerifier.verify("dead"))
				.thenThrow(new AccessTokenVerifier.InvalidTokenException("token expired"));
		when(tokenStateClient.introspect("dead"))
				.thenReturn(new RefreshTokenInfo(false, null, null, null, 0L, 0L));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", BASIC)
						.param("token", "dead"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false))
				.andExpect(jsonPath("$.sub").doesNotExist())
				.andExpect(jsonPath("$.client_id").doesNotExist())
				.andExpect(jsonPath("$.scope").doesNotExist())
				.andExpect(jsonPath("$.exp").doesNotExist());
	}

	@Test
	void missingCredentialsReturnsInvalidClient() throws Exception {
		mockMvc.perform(post("/oauth2/introspect").param("token", "whatever"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_client"));

		verify(tokenStateClient, never()).introspect(any());
	}

	@Test
	void wrongSecretReturnsInvalidClient() throws Exception {
		when(clientRegistryClient.getClient("article-api")).thenReturn(articleApi());
		String wrong = "Basic " + Base64.getEncoder()
				.encodeToString("article-api:nope".getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", wrong)
						.param("token", "whatever"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.error").value("invalid_client"));
	}

	@Test
	void missingTokenParameterReturnsInvalidRequest() throws Exception {
		when(clientRegistryClient.getClient("article-api")).thenReturn(articleApi());

		mockMvc.perform(post("/oauth2/introspect").header("Authorization", BASIC))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_request"));
	}
}
