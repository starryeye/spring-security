package dev.starryeye.client_registry;

import dev.starryeye.client_registry.jpa.ClientEntityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ClientControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired ClientEntityRepository repository;

	@Test
	void returnsSeededClient() throws Exception {
		mockMvc.perform(get("/internal/clients/my-client"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clientId").value("my-client"))
				.andExpect(jsonPath("$.redirectUris[0]").value("http://127.0.0.1:8080/callback"))
				.andExpect(jsonPath("$.scopes", org.hamcrest.Matchers.contains("openid", "profile")))
				.andExpect(jsonPath("$.clientSecretHash").exists());
	}

	@Test
	void unknownClientReturns404() throws Exception {
		mockMvc.perform(get("/internal/clients/no-such-client"))
				.andExpect(status().isNotFound());
	}
}
