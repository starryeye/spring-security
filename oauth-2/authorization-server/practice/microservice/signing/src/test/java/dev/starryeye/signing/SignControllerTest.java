package dev.starryeye.signing;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SignControllerTest {

	@Autowired MockMvc mockMvc;
	@Autowired JwkKeyProvider provider;

	@Test
	void signReturnsValidRs256Jwt() throws Exception {
		String body = """
			{"claims":{"sub":"user","iss":"http://localhost:9000"}}
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

		// Verify actual RS256 signature using public key
		RSAKey publicKey = provider.getPublicJwkSet().getKeys().get(0).toRSAKey();
		org.assertj.core.api.Assertions.assertThat(parsed.verify(new RSASSAVerifier(publicKey.toRSAPublicKey()))).isTrue();

		// Verify algorithm is RS256
		org.assertj.core.api.Assertions.assertThat(parsed.getHeader().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
	}

	@Test
	void jwksExposesPublicKeyOnly() throws Exception {
		mockMvc.perform(get("/oauth2/jwks"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.keys[0].kty").value("RSA"))
				.andExpect(jsonPath("$.keys[0].d").doesNotExist());
	}

	private String signWith(String typRequestFragment) throws Exception {
		String body = "{\"claims\":{\"sub\":\"user-sub-0001\"}" + typRequestFragment + "}";
		String response = mockMvc.perform(post("/internal/sign")
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return com.jayway.jsonpath.JsonPath.read(response, "$.jwt");
	}

	// 요청이 지정한 typ 이 헤더에 그대로 실려야 한다. 토큰 타입 혼동을 막는 유일한 표식이다.
	@Test
	void signsWithRequestedTyp() throws Exception {
		SignedJWT jwt = SignedJWT.parse(signWith(",\"typ\":\"at+jwt\""));
		assertThat(jwt.getHeader().getType().toString()).isEqualTo("at+jwt");
	}

	@Test
	void signsLogoutTokenTyp() throws Exception {
		SignedJWT jwt = SignedJWT.parse(signWith(",\"typ\":\"logout+jwt\""));
		assertThat(jwt.getHeader().getType().toString()).isEqualTo("logout+jwt");
	}

	// typ 을 안 보내는 구버전 호출자는 JWT 로 서명된다. at+jwt 를 요구하는 검증기는 그 토큰을 거부하므로
	// 구버전 token 서비스가 새 검증을 통과하는 access token 을 만들어낼 수 없다.
	@Test
	void defaultsToPlainJwtWhenTypIsAbsent() throws Exception {
		SignedJWT jwt = SignedJWT.parse(signWith(""));
		assertThat(jwt.getHeader().getType().toString()).isEqualTo("JWT");
	}

	@Test
	void signedJwtCarriesKeyIdInHeader() throws Exception {
		SignedJWT jwt = SignedJWT.parse(signWith(",\"typ\":\"at+jwt\""));
		assertThat(jwt.getHeader().getKeyID()).isNotBlank();
	}
}
