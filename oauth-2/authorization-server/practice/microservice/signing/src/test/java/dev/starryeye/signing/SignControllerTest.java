package dev.starryeye.signing;

import com.nimbusds.jose.JWSObject;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SignControllerTest {

	@Autowired MockMvc mockMvc;

	@Test
	void signReturnsValidRs256Jwt() throws Exception {
		String body = """
			{"claims":{"sub":"user","iss":"http://localhost:9000"},"header":{"kid":"signing-key-2026"}}
			""";
		String json = mockMvc.perform(post("/internal/sign")
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.jwt", notNullValue()))
				.andReturn().getResponse().getContentAsString();

		String jwt = com.jayway.jsonpath.JsonPath.read(json, "$.jwt");
		SignedJWT parsed = SignedJWT.parse(jwt);
		org.assertj.core.api.Assertions.assertThat(parsed.getState()).isEqualTo(JWSObject.State.SIGNED);
		org.assertj.core.api.Assertions.assertThat(parsed.getJWTClaimsSet().getSubject()).isEqualTo("user");
	}

	@Test
	void jwksExposesPublicKeyOnly() throws Exception {
		mockMvc.perform(get("/oauth2/jwks"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.keys[0].kty").value("RSA"))
				.andExpect(jsonPath("$.keys[0].d").doesNotExist());
	}
}
