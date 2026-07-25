package dev.starryeye.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthorizationCodeIssuerTest {

	@Test
	void issueStoresCodeWithTtlAndReturnsCode() throws Exception {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(ops);

		AuthorizationCodeIssuer issuer = new AuthorizationCodeIssuer(redis, new ObjectMapper(), 60);
		String code = issuer.issue("my-client", "http://127.0.0.1:8080/callback", "openid profile",
				"user-sub-0001", "chal", "n-0S6_WzA2Mj", 1700000000L);

		assertThat(code).isNotBlank();

		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
		verify(ops).set(keyCaptor.capture(), valueCaptor.capture(), eq(Duration.ofSeconds(60)));

		assertThat(keyCaptor.getValue()).isEqualTo("auth:code:" + code);
		Map<String, Object> json = new ObjectMapper()
				.readValue(valueCaptor.getValue(), new TypeReference<>() {});
		assertThat(json)
				.containsEntry("clientId", "my-client")
				.containsEntry("redirectUri", "http://127.0.0.1:8080/callback")
				.containsEntry("scope", "openid profile")
				.containsEntry("sub", "user-sub-0001")
				.containsEntry("codeChallenge", "chal")
				.containsEntry("nonce", "n-0S6_WzA2Mj")
				.containsEntry("authTime", 1700000000);
	}
}
