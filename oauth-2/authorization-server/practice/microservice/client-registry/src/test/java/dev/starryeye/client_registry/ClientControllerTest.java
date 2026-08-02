package dev.starryeye.client_registry;

import dev.starryeye.client_registry.dto.ClientResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClientController.class)
class ClientControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	ClientController.ClientLookupService lookupService;

	// my-client 처럼 사용자 위임 scope 는 있고 client 능력(clientScopes)은 없는 client.
	@Test
	void returnsClientWithUserDelegatedScopesOnly() throws Exception {
		when(lookupService.findByClientId("my-client")).thenReturn(new ClientResponse(
				"my-client",
				List.of("http://127.0.0.1:8080/callback"),
				List.of("openid", "profile"),
				"{bcrypt}$2a$10$hash",
				List.of("authorization_code"),
				List.of(),
				null,
				List.of()));

		mockMvc.perform(get("/internal/clients/my-client"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clientId").value("my-client"))
				.andExpect(jsonPath("$.redirectUris[0]").value("http://127.0.0.1:8080/callback"))
				.andExpect(jsonPath("$.scopes", contains("openid", "profile")))
				.andExpect(jsonPath("$.clientSecretHash").exists())
				.andExpect(jsonPath("$.grantTypes", contains("authorization_code")))
				.andExpect(jsonPath("$.clientScopes.length()").value(0))
				.andExpect(jsonPath("$.backchannelLogoutUri").doesNotExist())
				.andExpect(jsonPath("$.postLogoutRedirectUris.length()").value(0));
	}

	// article-api 처럼 사용자 위임 scope 없이 client 능력만 갖는 client.
	// scopes(사용자 위임)와 clientScopes(관리자 부여)가 응답에서 분리돼 실리는지 확인한다.
	@Test
	void returnsClientWithClientScopesOnly() throws Exception {
		when(lookupService.findByClientId("article-api")).thenReturn(new ClientResponse(
				"article-api",
				List.of(),
				List.of(),
				"{bcrypt}$2a$10$hash",
				List.of("client_credentials"),
				List.of("introspect"),
				null,
				List.of()));

		mockMvc.perform(get("/internal/clients/article-api"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clientScopes[0]").value("introspect"))
				.andExpect(jsonPath("$.clientScopes.length()").value(1))
				.andExpect(jsonPath("$.scopes.length()").value(0))
				.andExpect(jsonPath("$.backchannelLogoutUri").doesNotExist())
				.andExpect(jsonPath("$.postLogoutRedirectUris.length()").value(0));
	}

	// demo-rp: 로그아웃 URI 두 개가 각각 제 필드로 실리는지 확인한다.
	// redirect_uri 와 post_logout_redirect_uri 는 목적이 달라 절대 같은 필드로 나가면 안 된다.
	@Test
	void returnsClientWithLogoutUris() throws Exception {
		when(lookupService.findByClientId("demo-rp")).thenReturn(new ClientResponse(
				"demo-rp",
				List.of("http://localhost:8095/login/oauth2/code/microservice"),
				List.of("openid", "profile", "email"),
				"{bcrypt}$2a$10$hash",
				List.of("authorization_code", "refresh_token"),
				List.of(),
				"http://localhost:8095/logout/connect/back-channel/microservice",
				List.of("http://localhost:8095/")));

		mockMvc.perform(get("/internal/clients/demo-rp"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clientId").value("demo-rp"))
				.andExpect(jsonPath("$.redirectUris[0]")
						.value("http://localhost:8095/login/oauth2/code/microservice"))
				.andExpect(jsonPath("$.redirectUris.length()").value(1))
				.andExpect(jsonPath("$.backchannelLogoutUri")
						.value("http://localhost:8095/logout/connect/back-channel/microservice"))
				.andExpect(jsonPath("$.postLogoutRedirectUris[0]").value("http://localhost:8095/"))
				.andExpect(jsonPath("$.postLogoutRedirectUris.length()").value(1))
				.andExpect(jsonPath("$.clientScopes.length()").value(0));
	}

	@Test
	void unknownClientReturns404() throws Exception {
		when(lookupService.findByClientId("no-such-client"))
				.thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

		mockMvc.perform(get("/internal/clients/no-such-client"))
				.andExpect(status().isNotFound());
	}
}
