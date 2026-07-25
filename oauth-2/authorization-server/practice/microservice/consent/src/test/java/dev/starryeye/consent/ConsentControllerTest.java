package dev.starryeye.consent;

import dev.starryeye.consent.jpa.ConsentEntity;
import dev.starryeye.consent.jpa.ConsentEntityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsentController.class)
class ConsentControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	ConsentEntityRepository repository;

	@Test
	void returnsGrantedScopes() throws Exception {
		when(repository.findBySubAndClientId("user-sub-0001", "my-client")).thenReturn(
				Optional.of(ConsentEntity.builder().sub("user-sub-0001").clientId("my-client")
						.scopes("openid,profile").build()));

		mockMvc.perform(get("/internal/consents/user-sub-0001/my-client"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sub").value("user-sub-0001"))
				.andExpect(jsonPath("$.clientId").value("my-client"))
				.andExpect(jsonPath("$.scopes", containsInAnyOrder("openid", "profile")));
	}

	@Test
	void returnsEmptyScopesWhenNoConsentRecorded() throws Exception {
		when(repository.findBySubAndClientId("user-sub-0001", "unknown-client")).thenReturn(Optional.empty());

		mockMvc.perform(get("/internal/consents/user-sub-0001/unknown-client"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.scopes", hasSize(0)));
	}

	@Test
	void saveMergesWithExistingScopes() throws Exception {
		ConsentEntity existing = ConsentEntity.builder().sub("user-sub-0001").clientId("my-client")
				.scopes("openid,profile").build();
		when(repository.findBySubAndClientId("user-sub-0001", "my-client")).thenReturn(Optional.of(existing));
		when(repository.save(any(ConsentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		mockMvc.perform(post("/internal/consents")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sub\":\"user-sub-0001\",\"clientId\":\"my-client\",\"scopes\":[\"email\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.scopes", containsInAnyOrder("openid", "profile", "email")));
	}

	@Test
	void saveCreatesRecordWhenAbsent() throws Exception {
		when(repository.findBySubAndClientId("user-sub-0002", "my-client")).thenReturn(Optional.empty());
		when(repository.save(any(ConsentEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

		mockMvc.perform(post("/internal/consents")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"sub\":\"user-sub-0002\",\"clientId\":\"my-client\",\"scopes\":[\"openid\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sub").value("user-sub-0002"))
				.andExpect(jsonPath("$.scopes", containsInAnyOrder("openid")));
	}
}
