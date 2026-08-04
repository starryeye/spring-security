package dev.starryeye.auth;

import dev.starryeye.auth.client.ClientInfo;
import dev.starryeye.auth.client.ClientRegistryClient;
import dev.starryeye.auth.client.SessionClient;
import dev.starryeye.auth.security.SessionIdIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LogoutControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired SessionIdIssuer sessionIdIssuer;
	@MockitoBean IdTokenHintVerifier hintVerifier;
	@MockitoBean ClientRegistryClient clientRegistryClient;
	@MockitoBean SessionClient sessionClient;

	private MockHttpSession loggedInSession() {
		MockHttpSession session = new MockHttpSession();
		sessionIdIssuer.issue(session);
		return session;
	}

	private ClientInfo demoRp() {
		return new ClientInfo("demo-rp", List.of(), List.of("openid"), List.of("authorization_code"),
				List.of("http://localhost:8095/"));
	}

	@Test
	@WithMockUser("user-sub-0001")
	void redirectsToRegisteredPostLogoutUriAndNotifiesSession() throws Exception {
		when(hintVerifier.verify("hint")).thenReturn("demo-rp");
		when(clientRegistryClient.getClient("demo-rp")).thenReturn(demoRp());

		mockMvc.perform(get("/oauth2/logout").session(loggedInSession())
						.param("id_token_hint", "hint")
						.param("post_logout_redirect_uri", "http://localhost:8095/")
						.param("state", "xyz"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("http://localhost:8095/?state=xyz"));

		verify(sessionClient).logout(any());
	}

	// 미등록 주소로는 돌려보내지 않는다. authorize 의 redirect_uri 정확 일치와 같은 원칙(open redirect 방지).
	// 그래도 로그아웃은 수행한다 — 세션을 살려두는 것이 더 위험하다.
	@Test
	@WithMockUser("user-sub-0001")
	void doesNotRedirectToUnregisteredUriButStillLogsOut() throws Exception {
		when(hintVerifier.verify("hint")).thenReturn("demo-rp");
		when(clientRegistryClient.getClient("demo-rp")).thenReturn(demoRp());

		mockMvc.perform(get("/oauth2/logout").session(loggedInSession())
						.param("id_token_hint", "hint")
						.param("post_logout_redirect_uri", "http://evil.example/"))
				.andExpect(status().isOk());

		verify(sessionClient).logout(any());
	}

	// 힌트 검증이 실패해도 로그아웃은 한다. 검증은 어디로 돌려보낼지를 정할 때만 필요하다.
	@Test
	@WithMockUser("user-sub-0001")
	void logsOutEvenWhenHintIsInvalid() throws Exception {
		when(hintVerifier.verify(any()))
				.thenThrow(new IdTokenHintVerifier.InvalidHintException("signature verification failed"));

		mockMvc.perform(get("/oauth2/logout").session(loggedInSession())
						.param("id_token_hint", "forged")
						.param("post_logout_redirect_uri", "http://localhost:8095/"))
				.andExpect(status().isOk());

		verify(sessionClient).logout(any());
	}

	@Test
	@WithMockUser("user-sub-0001")
	void logsOutWithoutHint() throws Exception {
		mockMvc.perform(get("/oauth2/logout").session(loggedInSession()))
				.andExpect(status().isOk());

		verify(sessionClient).logout(any());
		verify(hintVerifier, never()).verify(any());
	}
}
