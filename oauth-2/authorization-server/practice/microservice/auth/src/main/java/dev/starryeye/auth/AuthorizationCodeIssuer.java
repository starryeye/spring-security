package dev.starryeye.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class AuthorizationCodeIssuer {

	/**
	 * authorization code 를 만들어 Redis 에 저장한다. (token 이 소비할 공유 계약)
	 *      key "auth:code:{code}", value 는 {clientId, redirectUri, scope, sub, codeChallenge} JSON, TTL 은 설정값(60초).
	 */

	private static final String KEY_PREFIX = "auth:code:";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final long ttlSeconds;

	public AuthorizationCodeIssuer(
			StringRedisTemplate redisTemplate,
			ObjectMapper objectMapper,
			@Value("${my.authorization-code-ttl-seconds}") long ttlSeconds
	) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		this.ttlSeconds = ttlSeconds;
	}

	public String issue(String clientId, String redirectUri, String scope, String sub, String codeChallenge) {
		String code = UUID.randomUUID().toString().replace("-", "");
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("clientId", clientId);
		data.put("redirectUri", redirectUri);
		data.put("scope", scope);
		data.put("sub", sub);
		data.put("codeChallenge", codeChallenge);
		try {
			redisTemplate.opsForValue().set(KEY_PREFIX + code, objectMapper.writeValueAsString(data), Duration.ofSeconds(ttlSeconds));
		} catch (Exception e) {
			throw new IllegalStateException("failed to store authorization code", e);
		}
		return code;
	}
}
