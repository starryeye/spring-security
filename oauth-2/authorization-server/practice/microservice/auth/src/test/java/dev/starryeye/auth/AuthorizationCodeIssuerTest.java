package dev.starryeye.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

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
		String code = issuer.issue("my-client", "http://127.0.0.1:8080/callback", "openid profile", "user-sub-0001", "chal");

		assertThat(code).isNotBlank();
		verify(ops).set(eq("auth:code:" + code), contains("\"sub\":\"user-sub-0001\""), eq(Duration.ofSeconds(60)));
	}
}
