package dev.starryeye.auth;

import dev.starryeye.auth.client.ClientInfo;
import dev.starryeye.auth.client.ClientRegistryClient;
import dev.starryeye.auth.client.ConsentClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(AuthorizeController.class)
class AuthorizeControllerTest {

	// authorize 는 nonce·authTime 을 code 로 넘기는 사슬의 출발점이자 동의 분기의 갈림길이다.
	// 여기서 값이 새면 id token 의 nonce 가 조용히 사라지고, redirect_uri/scope 검증이 풀리면 그대로 보안 구멍이 된다.

	private static final String REDIRECT_URI = "http://127.0.0.1:8080/callback";

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	ClientRegistryClient clientRegistryClient;

	@MockitoBean
	AuthorizationCodeIssuer codeIssuer;

	@MockitoBean
	ConsentClient consentClient;

	@MockitoBean
	PendingAuthorizationStore pendingStore;

	private ClientInfo clientInfo() {
		return new ClientInfo("my-client", List.of(REDIRECT_URI),
				List.of("openid", "profile", "email"), List.of("authorization_code"));
	}

	// 요청 scope 가 전부 기승인이면 동의 화면 없이 바로 code 를 발급한다.
	// nonce·authTime 이 codeIssuer 인자로 정확히 흘러야 한다 (id token 의 nonce·auth_time 이 여기서 결정된다).
	@Test
	void alreadyGrantedScopesIssueCodeDirectly() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(consentClient.getGrantedScopes("user-sub-0001", "my-client")).thenReturn(List.of("openid", "profile"));
		when(codeIssuer.issue(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong()))
				.thenReturn("issued-code");

		long before = Instant.now().getEpochSecond();
		mockMvc.perform(get("/oauth2/authorize").with(user("user-sub-0001"))
						.param("response_type", "code")
						.param("client_id", "my-client")
						.param("redirect_uri", REDIRECT_URI)
						.param("scope", "openid profile")
						.param("state", "xyz789")
						.param("code_challenge", "chal-123")
						.param("code_challenge_method", "S256")
						.param("nonce", "nonce-1"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl(REDIRECT_URI + "?code=issued-code&state=xyz789"));
		long after = Instant.now().getEpochSecond();

		ArgumentCaptor<Long> authTimeCaptor = ArgumentCaptor.forClass(Long.class);
		verify(codeIssuer).issue(eq("my-client"), eq(REDIRECT_URI), eq("openid profile"), eq("user-sub-0001"),
				eq("chal-123"), eq("nonce-1"), authTimeCaptor.capture());
		assertThat(authTimeCaptor.getValue()).isBetween(before, after);
		verify(pendingStore, never()).save(any());
	}

	// 미승인 scope 가 있으면 pending 을 저장하고 동의 화면을 렌더한다.
	// pending 에 nonce 가 담기지 않으면 동의 제출 이후 경로에서 nonce 가 사라진다.
	@Test
	void missingScopesRenderConsentPageWithPendingNonce() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());
		when(consentClient.getGrantedScopes("user-sub-0001", "my-client")).thenReturn(List.of("openid"));
		when(pendingStore.save(any(PendingAuthorization.class))).thenReturn("pending-1");

		mockMvc.perform(get("/oauth2/authorize").with(user("user-sub-0001"))
						.param("response_type", "code")
						.param("client_id", "my-client")
						.param("redirect_uri", REDIRECT_URI)
						.param("scope", "openid profile email")
						.param("state", "xyz789")
						.param("code_challenge", "chal-123")
						.param("code_challenge_method", "S256")
						.param("nonce", "nonce-1"))
				.andExpect(status().isOk())
				.andExpect(view().name("consent"))
				.andExpect(model().attribute("pendingId", "pending-1"))
				.andExpect(model().attribute("clientId", "my-client"))
				.andExpect(model().attribute("requestedScopes", List.of("profile", "email")))
				.andExpect(model().attribute("grantedScopes", List.of("openid")));

		ArgumentCaptor<PendingAuthorization> captor = ArgumentCaptor.forClass(PendingAuthorization.class);
		verify(pendingStore).save(captor.capture());
		PendingAuthorization pending = captor.getValue();
		assertThat(pending.nonce()).isEqualTo("nonce-1");
		assertThat(pending.clientId()).isEqualTo("my-client");
		assertThat(pending.redirectUri()).isEqualTo(REDIRECT_URI);
		assertThat(pending.scope()).isEqualTo("openid profile email"); // 요청 전체를 담는다 (미승인분만이 아니다)
		assertThat(pending.sub()).isEqualTo("user-sub-0001");
		assertThat(pending.codeChallenge()).isEqualTo("chal-123");
		assertThat(pending.state()).isEqualTo("xyz789");
		assertThat(pending.authTime()).isPositive();

		verify(codeIssuer, never()).issue(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong());
	}

	// 등록되지 않은 redirect_uri 로는 절대 redirect 하지 않는다 (open redirect 방지).
	// 이 검증이 풀리면 공격자가 error 파라미터를 실어 임의 주소로 사용자를 보낼 수 있다.
	@Test
	void unregisteredRedirectUriDoesNotRedirect() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());

		mockMvc.perform(get("/oauth2/authorize").with(user("user-sub-0001"))
						.param("response_type", "code")
						.param("client_id", "my-client")
						.param("redirect_uri", "http://evil.example.com/callback")
						.param("scope", "openid")
						.param("state", "xyz789")
						.param("code_challenge", "chal-123")
						.param("code_challenge_method", "S256"))
				.andExpect(status().isBadRequest())
				.andExpect(redirectedUrl(null));

		verify(codeIssuer, never()).issue(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong());
		verify(pendingStore, never()).save(any());
	}

	// client 에 등록되지 않은 scope 는 invalid_scope 로 거부한다 (여기부터는 redirect_uri 가 검증됐으므로 redirect 로 알린다)
	@Test
	void scopeBeyondClientRegistrationReturnsInvalidScope() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());

		mockMvc.perform(get("/oauth2/authorize").with(user("user-sub-0001"))
						.param("response_type", "code")
						.param("client_id", "my-client")
						.param("redirect_uri", REDIRECT_URI)
						.param("scope", "openid admin")
						.param("state", "xyz789")
						.param("code_challenge", "chal-123")
						.param("code_challenge_method", "S256"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl(REDIRECT_URI + "?error=invalid_scope&state=xyz789"));

		verify(codeIssuer, never()).issue(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong());
	}

	// PKCE 는 필수다. code_challenge 가 없으면 invalid_request.
	@Test
	void missingCodeChallengeReturnsInvalidRequest() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());

		mockMvc.perform(get("/oauth2/authorize").with(user("user-sub-0001"))
						.param("response_type", "code")
						.param("client_id", "my-client")
						.param("redirect_uri", REDIRECT_URI)
						.param("scope", "openid")
						.param("state", "xyz789"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl(REDIRECT_URI + "?error=invalid_request&state=xyz789"));

		verify(codeIssuer, never()).issue(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong());
	}

	// S256 만 허용한다. plain 은 challenge 가 곧 verifier 라 PKCE 의 방어 효과가 없다.
	@Test
	void plainCodeChallengeMethodReturnsInvalidRequest() throws Exception {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());

		mockMvc.perform(get("/oauth2/authorize").with(user("user-sub-0001"))
						.param("response_type", "code")
						.param("client_id", "my-client")
						.param("redirect_uri", REDIRECT_URI)
						.param("scope", "openid")
						.param("state", "xyz789")
						.param("code_challenge", "chal-123")
						.param("code_challenge_method", "plain"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl(REDIRECT_URI + "?error=invalid_request&state=xyz789"));

		verify(codeIssuer, never()).issue(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong());
	}
}
