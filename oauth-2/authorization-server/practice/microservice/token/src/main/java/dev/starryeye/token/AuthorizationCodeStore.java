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
		String json = redisTemplate.opsForValue().getAndDelete(key); // 원자적 조회+삭제(Redis GETDEL).. code 재사용(replay) 방지 (RFC 6749 4.1.2)
		if (json == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(objectMapper.readValue(json, AuthorizationCodeData.class));
		} catch (Exception e) {
			return Optional.empty();
		}
	}
}
