package dev.starryeye.demo_rp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
		// discovery 를 타면 인가 서버가 떠 있어야 하므로, 테스트에서는 엔드포인트를 직접 지정한다.
		// 주의. issuer-uri 를 빈 문자열로 덮어써도 소용없다 — Boot 의 OAuth2ClientPropertiesMapper 는
		// null 여부만 검사하고 빈 문자열은 그대로 ClientRegistrations.fromIssuerLocation("") 에 넘겨
		// "issuer cannot be empty" 로 컨텍스트 로딩이 죽는다. 그래서 registration 이 참조하는 provider id 자체를
		// 별도로("microservice-test") 두어 메인 설정의 issuer-uri 가 아예 조회되지 않게 한다.
		"spring.security.oauth2.client.registration.microservice.provider=microservice-test",
		"spring.security.oauth2.client.provider.microservice-test.authorization-uri=http://localhost:9000/oauth2/authorize",
		"spring.security.oauth2.client.provider.microservice-test.token-uri=http://localhost:9000/oauth2/token",
		"spring.security.oauth2.client.provider.microservice-test.jwk-set-uri=http://localhost:9000/oauth2/jwks",
		"spring.security.oauth2.client.provider.microservice-test.user-info-uri=http://localhost:9000/userinfo",
		"spring.security.oauth2.client.provider.microservice-test.user-name-attribute=sub"
})
class SecurityConfigTest {

	@Autowired MockMvc mockMvc;

	// 보호 페이지는 미인증이면 로그인으로 보낸다. e2e 의 로그아웃 성공 판정이 이 동작에 기댄다.
	@Test
	void protectedPageRedirectsWhenNotAuthenticated() throws Exception {
		mockMvc.perform(get("/me")).andExpect(status().is3xxRedirection());
	}

	// back-channel 수신 경로는 인증을 요구하지 않아야 한다. 요구하면 OP 의 POST 가 로그인으로 튕겨
	// 로그아웃이 조용히 실패한다. logout_token 이 없으므로 성공하지는 않지만, 거절 사유가
	// "인증이 없다"(302/401)여서는 안 된다. 그 두 가지가 아님을 단언한다.
	@Test
	void backChannelEndpointIsReachableWithoutAuthentication() throws Exception {
		int status = mockMvc.perform(post("/logout/connect/back-channel/microservice"))
				.andReturn().getResponse().getStatus();

		assertThat(status).isNotEqualTo(302).isNotEqualTo(401);
	}

	// 이 AS 는 client 종류와 무관하게 PKCE(S256)를 요구한다(OAuth 2.1 방향). 그런데 Spring Security 는
	// confidential client 에 PKCE 를 자동으로 붙이지 않으므로, withPkce() 배선이 없으면 로그인이
	// authorize 단계에서 invalid_request 로 끝난다. 그 배선이 살아 있는지 고정한다.
	@Test
	void authorizationRequestCarriesPkce() throws Exception {
		String location = mockMvc.perform(get("/oauth2/authorization/microservice"))
				.andExpect(status().is3xxRedirection())
				.andReturn().getResponse().getHeader("Location");

		assertThat(location).contains("code_challenge=").contains("code_challenge_method=S256");
	}
}
