package dev.starryeye.client_registry;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ClientControllerIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	// 시드된 demo-rp 를 대상으로 실제 변환 로직을 검증한다.
	// 이 테스트는 postLogoutRedirectUris 가 redirectUris 와 다르다는 것을 보장한다.
	@Test
	void demo_rp_logout_uris_from_database() throws Exception {
		mockMvc.perform(get("/internal/clients/demo-rp"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.clientId").value("demo-rp"))
				.andExpect(jsonPath("$.redirectUris[0]")
						.value("http://localhost:8095/login/oauth2/code/microservice"))
				.andExpect(jsonPath("$.redirectUris.length()").value(1))
				.andExpect(jsonPath("$.backchannelLogoutUri")
						.value("http://localhost:8095/logout/connect/back-channel/microservice"))
				.andExpect(jsonPath("$.postLogoutRedirectUris[0]").value("http://localhost:8095/"))
				.andExpect(jsonPath("$.postLogoutRedirectUris.length()").value(1));
	}
}
