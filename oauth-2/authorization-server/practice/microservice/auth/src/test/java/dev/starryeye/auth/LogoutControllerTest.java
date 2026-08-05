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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 이 클래스만 SessionAutoConfiguration 을 꺼서 돈다. Spring Session 의 SessionRepositoryFilter 는
// 세션을 쿠키로만 조회하므로(spring-session-core 소스 확인) .session(mockHttpSession) 으로 직접 붙인
// 세션을 무시한다 — spring.session.store-type 은 Boot 3.4 의 SessionProperties 에서 이미 사라진 죽은
// 키라 그걸로는 못 끈다. 이 프로퍼티를 build.gradle 의 test 태스크에 걸면 모듈의 다른 모든 테스트가
// 조용히 같은 예외를 상속받으므로, 필요한 이 클래스에만 좁혀서 건다.
@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration")
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
		MockHttpSession session = loggedInSession();
		String sid = sessionIdIssuer.currentSid(session);

		mockMvc.perform(get("/oauth2/logout").session(session)
						.param("id_token_hint", "hint")
						.param("post_logout_redirect_uri", "http://localhost:8095/")
						.param("state", "xyz"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("http://localhost:8095/?state=xyz"));

		// HTTP 세션 id 가 아니라 sessionIdIssuer 가 실제로 발급한 sid 와 대조한다 — SessionIdIssuer javadoc 이
		// 명시적으로 금지하는 실수(HTTP 세션 id 를 sid 로 흘리는 것)를 any() 로는 잡을 수 없다.
		verify(sessionClient).logout(eq(sid));
	}

	// 미등록 주소로는 돌려보내지 않는다. authorize 의 redirect_uri 정확 일치와 같은 원칙(open redirect 방지).
	// 그래도 로그아웃은 수행한다 — 세션을 살려두는 것이 더 위험하다.
	@Test
	@WithMockUser("user-sub-0001")
	void doesNotRedirectToUnregisteredUriButStillLogsOut() throws Exception {
		when(hintVerifier.verify("hint")).thenReturn("demo-rp");
		when(clientRegistryClient.getClient("demo-rp")).thenReturn(demoRp());
		MockHttpSession session = loggedInSession();
		String sid = sessionIdIssuer.currentSid(session);

		mockMvc.perform(get("/oauth2/logout").session(session)
						.param("id_token_hint", "hint")
						.param("post_logout_redirect_uri", "http://evil.example/"))
				.andExpect(status().isOk());

		verify(sessionClient).logout(eq(sid));
	}

	// 힌트 검증이 실패해도 로그아웃은 한다. 검증은 어디로 돌려보낼지를 정할 때만 필요하다.
	@Test
	@WithMockUser("user-sub-0001")
	void logsOutEvenWhenHintIsInvalid() throws Exception {
		when(hintVerifier.verify(any()))
				.thenThrow(new IdTokenHintVerifier.InvalidHintException("signature verification failed"));
		MockHttpSession session = loggedInSession();
		String sid = sessionIdIssuer.currentSid(session);

		mockMvc.perform(get("/oauth2/logout").session(session)
						.param("id_token_hint", "forged")
						.param("post_logout_redirect_uri", "http://localhost:8095/"))
				.andExpect(status().isOk());

		verify(sessionClient).logout(eq(sid));
	}

	@Test
	@WithMockUser("user-sub-0001")
	void logsOutWithoutHint() throws Exception {
		MockHttpSession session = loggedInSession();
		String sid = sessionIdIssuer.currentSid(session);

		mockMvc.perform(get("/oauth2/logout").session(session))
				.andExpect(status().isOk());

		verify(sessionClient).logout(eq(sid));
		verify(hintVerifier, never()).verify(any());
	}

	// 이미 로그아웃한 사용자가 다시 로그아웃하는 것은 오류가 아니다. 끊을 세션이 없으면 통지도 없다.
	@Test
	void logoutWithoutSessionIsNotAnError() throws Exception {
		mockMvc.perform(get("/oauth2/logout"))
				.andExpect(status().is2xxSuccessful());

		verify(sessionClient, never()).logout(any());
	}
}
