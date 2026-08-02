package dev.starryeye.token;

import dev.starryeye.token.client.SigningClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class AccessTokenIssuerTest {

	@Autowired
	AccessTokenIssuer accessTokenIssuer;

	@MockitoBean
	SigningClient signingClient;

	// RFC 9068 2.1 이 access token 에 규정한 typ 이 실제로 signing 호출에 실리는지 확인한다.
	// 이 typ 이 없으면 AccessTokenVerifier 가 발급된 토큰을 스스로 거부하게 된다.
	@Test
	void signsWithAccessTokenTyp() {
		when(signingClient.sign(anyMap(), any())).thenReturn("signed-access-token");

		String token = accessTokenIssuer.issue("user-sub-0001", "my-client", "openid profile");

		assertThat(token).isEqualTo("signed-access-token");

		ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
		ArgumentCaptor<String> typCaptor = ArgumentCaptor.forClass(String.class);
		verify(signingClient).sign(claimsCaptor.capture(), typCaptor.capture());

		assertThat(typCaptor.getValue()).isEqualTo("at+jwt");
		Map<String, Object> claims = claimsCaptor.getValue();
		assertThat(claims).containsEntry("iss", "http://localhost:9000");
		assertThat(claims).containsEntry("sub", "user-sub-0001");
		assertThat(claims).containsEntry("aud", "my-client");
		assertThat(claims).containsKeys("iat", "exp");
		assertThat(claims).containsEntry("scope", List.of("openid", "profile"));
	}
}
