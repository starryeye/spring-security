package dev.starryeye.token;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AuthorizationCodeStore {

	/**
	 * auth 가 Redis 에 저장한 authorization code 를 조회하고 즉시 삭제한다. (1회용)
	 *      key 형식과 JSON 필드는 auth 와 합의한 공유 계약이다. ("auth:code:{code}")
	 */

	private static final String KEY_PREFIX = "auth:code:";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper = new ObjectMapper();

	public Optional<AuthorizationCodeData> consume(String code) {
		String key = KEY_PREFIX + code;
		String json = redisTemplate.opsForValue().get(key);
		if (json == null) {
			return Optional.empty();
		}
		redisTemplate.delete(key); // 1회용 소비
		try {
			return Optional.of(objectMapper.readValue(json, AuthorizationCodeData.class));
		} catch (Exception e) {
			return Optional.empty();
		}
	}
}
