package dev.starryeye.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingAuthorizationStoreTest {

	@Test
	void saveStoresPendingWithTtlAndReturnsId() throws Exception {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(ops);

		PendingAuthorizationStore store = new PendingAuthorizationStore(redis, new ObjectMapper(), 300);
		String pendingId = store.save(new PendingAuthorization(
				"my-client", "http://127.0.0.1:8080/callback", "openid profile",
				"user-sub-0001", "chal", "xyz789", "n-0S6_WzA2Mj", 1700000000L, "sid-0001"));

		assertThat(pendingId).isNotBlank();

		ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
		verify(ops).set(keyCaptor.capture(), valueCaptor.capture(), eq(Duration.ofSeconds(300)));

		assertThat(keyCaptor.getValue()).isEqualTo("auth:pending:" + pendingId);
		Map<String, Object> json = new ObjectMapper().readValue(valueCaptor.getValue(),
				new com.fasterxml.jackson.core.type.TypeReference<>() {});
		assertThat(json)
				.containsEntry("clientId", "my-client")
				.containsEntry("redirectUri", "http://127.0.0.1:8080/callback")
				.containsEntry("scope", "openid profile")
				.containsEntry("sub", "user-sub-0001")
				.containsEntry("codeChallenge", "chal")
				.containsEntry("state", "xyz789")
				.containsEntry("nonce", "n-0S6_WzA2Mj")
				.containsEntry("sid", "sid-0001");
	}

	@Test
	void consumeReadsAndDeletesAtomically() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(ops);
		when(ops.getAndDelete("auth:pending:p1")).thenReturn(
				"{\"clientId\":\"my-client\",\"redirectUri\":\"http://127.0.0.1:8080/callback\","
						+ "\"scope\":\"openid profile\",\"sub\":\"user-sub-0001\",\"codeChallenge\":\"chal\","
						+ "\"state\":\"xyz789\",\"nonce\":\"n-0S6_WzA2Mj\",\"authTime\":1700000000,"
						+ "\"sid\":\"sid-0001\"}");

		PendingAuthorizationStore store = new PendingAuthorizationStore(redis, new ObjectMapper(), 300);
		Optional<PendingAuthorization> result = store.consume("p1");

		assertThat(result).isPresent();
		assertThat(result.get().sub()).isEqualTo("user-sub-0001");
		assertThat(result.get().nonce()).isEqualTo("n-0S6_WzA2Mj");
		assertThat(result.get().sid()).isEqualTo("sid-0001");
		verify(ops).getAndDelete("auth:pending:p1");
	}

	@Test
	void consumeMissingReturnsEmpty() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(ops);
		when(ops.getAndDelete("auth:pending:none")).thenReturn(null);

		PendingAuthorizationStore store = new PendingAuthorizationStore(redis, new ObjectMapper(), 300);
		assertThat(store.consume("none")).isEmpty();
	}
}
