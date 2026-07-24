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

	@Test
	void returnsClient() throws Exception {
		when(lookupService.findByClientId("my-client")).thenReturn(new ClientResponse(
				"my-client",
				List.of("http://127.0.0.1:8080/callback"),
				List.of("openid", "profile"),
				"{bcrypt}$2a$10$hash",
				List.of("authorization_code")));

		mockMvc.perform(get("/internal/clients/my-client"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clientId").value("my-client"))
				.andExpect(jsonPath("$.redirectUris[0]").value("http://127.0.0.1:8080/callback"))
				.andExpect(jsonPath("$.scopes", contains("openid", "profile")))
				.andExpect(jsonPath("$.clientSecretHash").exists())
				.andExpect(jsonPath("$.grantTypes", contains("authorization_code")));
	}

	@Test
	void unknownClientReturns404() throws Exception {
		when(lookupService.findByClientId("no-such-client"))
				.thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

		mockMvc.perform(get("/internal/clients/no-such-client"))
				.andExpect(status().isNotFound());
	}
}
