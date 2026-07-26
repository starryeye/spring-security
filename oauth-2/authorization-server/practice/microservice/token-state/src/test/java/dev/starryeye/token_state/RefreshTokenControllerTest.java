package dev.starryeye.token_state;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RefreshTokenEntityRepository repository;

	@Autowired
	private RefreshTokenService service;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void clean() {
		repository.deleteAll();
	}

	private String json(Map<String, Object> body) throws Exception {
		return objectMapper.writeValueAsString(body);
	}

	@Test
	void issueReturnsTokenAndFamily() throws Exception {
		mockMvc.perform(post("/internal/refresh-tokens")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("clientId", "my-client", "sub", "user-sub-0001",
								"scope", "openid offline_access", "authTime", 1700000000L))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.refreshToken").isNotEmpty())
				.andExpect(jsonPath("$.familyId").isNotEmpty())
				.andExpect(jsonPath("$.expiresAt").isNumber());
	}

	@Test
	void rotateReturnsRotatedStatusAndNewToken() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);

		mockMvc.perform(post("/internal/refresh-tokens/rotate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("refreshToken", issued.refreshToken(), "clientId", "my-client"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("ROTATED"))
				.andExpect(jsonPath("$.sub").value("user-sub-0001"))
				.andExpect(jsonPath("$.scope").value("openid"))
				.andExpect(jsonPath("$.authTime").value(1700000000L))
				.andExpect(jsonPath("$.refreshToken").isNotEmpty());
	}

	@Test
	void rotateWithReusedTokenReturnsReuseDetected() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);
		service.rotate(issued.refreshToken(), "my-client");

		mockMvc.perform(post("/internal/refresh-tokens/rotate")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("refreshToken", issued.refreshToken(), "clientId", "my-client"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("REUSE_DETECTED"))
				.andExpect(jsonPath("$.refreshToken").doesNotExist());
	}

	@Test
	void revokeReturnsRevokedFlag() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);

		mockMvc.perform(post("/internal/refresh-tokens/revoke")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("refreshToken", issued.refreshToken(), "clientId", "my-client"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.revoked").value(true));
	}

	@Test
	void introspectReturnsActiveClaims() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1700000000L);

		mockMvc.perform(post("/internal/refresh-tokens/introspect")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("refreshToken", issued.refreshToken()))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.sub").value("user-sub-0001"))
				.andExpect(jsonPath("$.clientId").value("my-client"))
				.andExpect(jsonPath("$.scope").value("openid offline_access"));
	}

	// 비활성 응답에서 나머지 필드가 새지 않아야 한다
	@Test
	void introspectInactiveOmitsClaims() throws Exception {
		mockMvc.perform(post("/internal/refresh-tokens/introspect")
						.contentType(MediaType.APPLICATION_JSON)
						.content(json(Map.of("refreshToken", "no-such-token"))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(false))
				.andExpect(jsonPath("$.sub").doesNotExist())
				.andExpect(jsonPath("$.clientId").doesNotExist())
				.andExpect(jsonPath("$.scope").doesNotExist());
	}
}
