package dev.starryeye.token;

import dev.starryeye.token.client.ClientRegistryClient;
import dev.starryeye.token.client.RefreshTokenInfo;
import dev.starryeye.token.client.SigningClient;
import dev.starryeye.token.client.TokenStateClient;
import dev.starryeye.token.client.UserDirectoryClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class IntrospectionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AccessTokenVerifier accessTokenVerifier;

	@MockitoBean
	private TokenStateClient tokenStateClient;

	@MockitoBean
	private ClientRegistryClient clientRegistryClient;

	@MockitoBean
	private SigningClient signingClient;

	@MockitoBean
	private UserDirectoryClient userDirectoryClient;

	@MockitoBean
	private AuthorizationCodeStore codeStore;

	private static final String CALLER = "Bearer caller-token";

	private void callerHasIntrospectScope() {
		when(accessTokenVerifier.verify("caller-token")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("article-api", List.of("introspect"),
						"article-api", 1800000000L, 1700000000L));
	}

	// 호출자와 토큰 주인이 다른 것이 introspection 의 정상 상황이다.
	@Test
	void callerWithIntrospectScopeCanInspectAnotherClientsAccessToken() throws Exception {
		callerHasIntrospectScope();
		when(accessTokenVerifier.verify("subject-token")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("user-sub-0001", List.of("openid", "profile"),
						"my-client", 1800000000L, 1700000000L));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", CALLER)
						.param("token", "subject-token"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.sub").value("user-sub-0001"))
				.andExpect(jsonPath("$.client_id").value("my-client"))
				.andExpect(jsonPath("$.scope").value("openid profile"))
				.andExpect(jsonPath("$.token_type").value("Bearer"));

		verify(tokenStateClient, never()).introspect(any());
	}

	// scope 가 없으면 인증은 됐지만 권한이 없는 것이라 403 이다 (RFC 6750 3.1)
	@Test
	void callerWithoutIntrospectScopeIsForbidden() throws Exception {
		when(accessTokenVerifier.verify("caller-token")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("my-client", List.of("openid"),
						"my-client", 1800000000L, 1700000000L));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", CALLER)
						.param("token", "subject-token"))
				.andExpect(status().isForbidden())
				.andExpect(header().string("WWW-Authenticate", "Bearer error=\"insufficient_scope\""));

		verify(tokenStateClient, never()).introspect(any());
	}

	// Basic 은 더 이상 받지 않는다. 계속 받으면 scope 로 좁힌 의미가 사라진다.
	@Test
	void basicCredentialsAreNoLongerAccepted() throws Exception {
		String basic = "Basic " + Base64.getEncoder()
				.encodeToString("article-api:secret".getBytes(StandardCharsets.UTF_8));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", basic)
						.param("token", "subject-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"));

		verify(accessTokenVerifier, never()).verify(any());
		verify(tokenStateClient, never()).introspect(any());
	}

	@Test
	void missingAuthorizationHeaderIsUnauthorized() throws Exception {
		mockMvc.perform(post("/oauth2/introspect").param("token", "subject-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"));

		verify(tokenStateClient, never()).introspect(any());
	}

	@Test
	void invalidCallerTokenIsUnauthorized() throws Exception {
		when(accessTokenVerifier.verify("caller-token"))
				.thenThrow(new AccessTokenVerifier.InvalidTokenException("token expired"));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", CALLER)
						.param("token", "subject-token"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""));

		verify(tokenStateClient, never()).introspect(any());
	}

	// 호출자 토큰 검증 중 jwks 를 못 구한 것은 토큰의 죄가 아니라 서버 장애다.
	@Test
	void signingFailureWhileVerifyingCallerReturns500() throws Exception {
		when(accessTokenVerifier.verify("caller-token"))
				.thenThrow(new IllegalStateException("signing is down"));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", CALLER)
						.param("token", "subject-token"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("server_error"));

		verify(tokenStateClient, never()).introspect(any());
	}

	// 컨트롤러의 두 catch(InvalidTokenException) 블록 중 "검사 대상" 토큰 쪽이다 — 호출자 쪽(위 테스트)과는
	// 다른 지점이라, 이 블록이 나중에 catch(Exception) 으로 넓어지거나 장애를 token-state 폴백으로 흘려도
	// 위 테스트만으로는 잡히지 않는다. verify(never()) 로 폴백 미발생까지 확인해야 이 지점을 덮는다.
	@Test
	void signingFailureWhileVerifyingSubjectTokenReturns500() throws Exception {
		callerHasIntrospectScope();
		when(accessTokenVerifier.verify("subject-token"))
				.thenThrow(new IllegalStateException("signing is down"));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", CALLER)
						.param("token", "subject-token"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("server_error"));

		verify(tokenStateClient, never()).introspect(any());
	}

	@Test
	void missingTokenParameterIsInvalidRequest() throws Exception {
		callerHasIntrospectScope();

		mockMvc.perform(post("/oauth2/introspect").header("Authorization", CALLER))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("invalid_request"));
	}

	// JWT 가 아니면 refresh token 일 수 있으므로 소유자에게 묻는다. token_type_hint 는 쓰지 않는다.
	@Test
	void nonJwtSubjectTokenFallsBackToTokenState() throws Exception {
		callerHasIntrospectScope();
		when(accessTokenVerifier.verify("opaque-refresh"))
				.thenThrow(new AccessTokenVerifier.InvalidTokenException("malformed token"));
		when(tokenStateClient.introspect("opaque-refresh")).thenReturn(
				new RefreshTokenInfo(true, "user-sub-0001", "my-client", "openid offline_access",
						1800000000L, 1700000000L));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", CALLER)
						.param("token", "opaque-refresh"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.scope").value("openid offline_access"))
				.andExpect(jsonPath("$.token_type").doesNotExist());
	}

	// 비활성 응답에서는 어떤 정보도 새지 않아야 한다 (RFC 7662 2.2)
	@Test
	void inactiveResponseContainsOnlyActiveFalse() throws Exception {
		callerHasIntrospectScope();
		when(accessTokenVerifier.verify("dead"))
				.thenThrow(new AccessTokenVerifier.InvalidTokenException("token expired"));
		when(tokenStateClient.introspect("dead"))
				.thenReturn(new RefreshTokenInfo(false, null, null, null, 0L, 0L));

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", CALLER)
						.param("token", "dead"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false))
				.andExpect(jsonPath("$.sub").doesNotExist())
				.andExpect(jsonPath("$.client_id").doesNotExist())
				.andExpect(jsonPath("$.scope").doesNotExist())
				.andExpect(jsonPath("$.exp").doesNotExist());
	}

	// token-state 가 빈 본문을 주면 "비활성" 이 아니라 "확인하지 못했다" 이므로 500 이다.
	@Test
	void nullIntrospectionResultReturns500NotInactive() throws Exception {
		callerHasIntrospectScope();
		when(accessTokenVerifier.verify("opaque"))
				.thenThrow(new AccessTokenVerifier.InvalidTokenException("malformed token"));
		when(tokenStateClient.introspect("opaque")).thenReturn(null);

		mockMvc.perform(post("/oauth2/introspect")
						.header("Authorization", CALLER)
						.param("token", "opaque"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("server_error"));
	}
}
