package dev.starryeye.auth;

import dev.starryeye.auth.client.ConsentClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsentPageController.class)
class ConsentPageControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	PendingAuthorizationStore pendingStore;

	@MockitoBean
	ConsentClient consentClient;

	@MockitoBean
	AuthorizationCodeIssuer codeIssuer;

	private PendingAuthorization pending() {
		return new PendingAuthorization("my-client", "http://127.0.0.1:8080/callback",
				"openid profile email", "user-sub-0001", "chal", "xyz789", "nonce-1", 1700000000L, "sid-0001");
	}

	// pending 의 nonce·authTime 이 code 발급 인자로 그대로 흘러야 한다.
	// 이 사슬이 끊기면 id token 의 nonce 가 사라져 replay 방어가 조용히 없어진다.
	@Test
	void approvedScopesAreSavedAndCodeIssued() throws Exception {
		when(pendingStore.consume("p1")).thenReturn(Optional.of(pending()));
		when(consentClient.getGrantedScopes("user-sub-0001", "my-client")).thenReturn(List.of());
		when(codeIssuer.issue(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString()))
				.thenReturn("issued-code");

		mockMvc.perform(post("/oauth2/consent").with(user("user-sub-0001")).with(csrf())
						.param("pending_id", "p1")
						.param("scope", "openid").param("scope", "profile"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("http://127.0.0.1:8080/callback?code=issued-code*"));

		verify(consentClient).saveConsent(eq("user-sub-0001"), eq("my-client"), any());
		verify(codeIssuer).issue(eq("my-client"), eq("http://127.0.0.1:8080/callback"), eq("openid profile"),
				eq("user-sub-0001"), eq("chal"), eq("nonce-1"), eq(1700000000L), eq("sid-0001"));
	}

	@Test
	void scopesBeyondPendingAreIgnored() throws Exception {
		when(pendingStore.consume("p1")).thenReturn(Optional.of(pending()));
		when(consentClient.getGrantedScopes("user-sub-0001", "my-client")).thenReturn(List.of());
		when(codeIssuer.issue(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString()))
				.thenReturn("issued-code");

		// pending 에 없는 admin 을 끼워 제출해도 승인되면 안 된다 (폼 조작 방어)
		mockMvc.perform(post("/oauth2/consent").with(user("user-sub-0001")).with(csrf())
						.param("pending_id", "p1")
						.param("scope", "openid").param("scope", "admin"))
				.andExpect(status().is3xxRedirection());

		verify(consentClient).saveConsent("user-sub-0001", "my-client", List.of("openid"));
		verify(codeIssuer).issue(eq("my-client"), eq("http://127.0.0.1:8080/callback"), eq("openid"),
				eq("user-sub-0001"), eq("chal"), eq("nonce-1"), eq(1700000000L), eq("sid-0001"));
	}

	@Test
	void denyingEverythingRedirectsWithAccessDenied() throws Exception {
		when(pendingStore.consume("p1")).thenReturn(Optional.of(pending()));

		mockMvc.perform(post("/oauth2/consent").with(user("user-sub-0001")).with(csrf())
						.param("pending_id", "p1"))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrlPattern("http://127.0.0.1:8080/callback?error=access_denied*"));

		verify(codeIssuer, never()).issue(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString());
	}

	@Test
	void unknownPendingReturnsErrorPageWithoutRedirect() throws Exception {
		when(pendingStore.consume("gone")).thenReturn(Optional.empty());

		mockMvc.perform(post("/oauth2/consent").with(user("user-sub-0001")).with(csrf())
						.param("pending_id", "gone")
						.param("scope", "openid"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void previouslyGrantedScopesAreUnionedIntoIssuedCode() throws Exception {
		when(pendingStore.consume("p1")).thenReturn(Optional.of(pending()));
		when(consentClient.getGrantedScopes("user-sub-0001", "my-client")).thenReturn(List.of("openid"));
		when(codeIssuer.issue(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString()))
				.thenReturn("issued-code");

		// 이번엔 profile 만 체크했지만, 이미 승인한 openid 가 code 의 scope 에 합쳐져야 한다
		mockMvc.perform(post("/oauth2/consent").with(user("user-sub-0001")).with(csrf())
						.param("pending_id", "p1")
						.param("scope", "profile"))
				.andExpect(status().is3xxRedirection());

		org.mockito.ArgumentCaptor<String> scopeCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
		verify(codeIssuer).issue(eq("my-client"), eq("http://127.0.0.1:8080/callback"), scopeCaptor.capture(),
				eq("user-sub-0001"), eq("chal"), eq("nonce-1"), eq(1700000000L), eq("sid-0001"));
		assertThat(scopeCaptor.getValue().split(" ")).containsExactlyInAnyOrder("openid", "profile");
	}

	@Test
	void submittingAnotherUsersPendingIsForbidden() throws Exception {
		when(pendingStore.consume("p1")).thenReturn(Optional.of(pending())); // pending.sub() = user-sub-0001

		mockMvc.perform(post("/oauth2/consent").with(user("someone-else")).with(csrf())
						.param("pending_id", "p1")
						.param("scope", "openid"))
				.andExpect(status().isForbidden());

		verify(codeIssuer, never()).issue(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong(), anyString());
	}
}
