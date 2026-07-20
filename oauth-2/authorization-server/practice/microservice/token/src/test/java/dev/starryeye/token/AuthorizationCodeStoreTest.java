package dev.starryeye.token;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AuthorizationCodeStoreTest {

	@Test
	void consumeReadsThenDeletes() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(ops);
		when(ops.getAndDelete("auth:code:abc")).thenReturn(
				"{\"clientId\":\"my-client\",\"redirectUri\":\"http://127.0.0.1:8080/callback\",\"scope\":\"openid profile\",\"sub\":\"user-sub-0001\",\"codeChallenge\":\"chal\"}");

		AuthorizationCodeStore store = new AuthorizationCodeStore(redis);
		Optional<AuthorizationCodeData> result = store.consume("abc");

		assertThat(result).isPresent();
		assertThat(result.get().sub()).isEqualTo("user-sub-0001");
		verify(ops).getAndDelete("auth:code:abc");
	}

	@Test
	void consumeMissingReturnsEmpty() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(ops);
		when(ops.getAndDelete("auth:code:none")).thenReturn(null);

		AuthorizationCodeStore store = new AuthorizationCodeStore(redis);
		assertThat(store.consume("none")).isEmpty();
	}
}
