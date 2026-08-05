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
	 *      key "auth:code:{code}", value 는 {clientId, redirectUri, scope, sub, codeChallenge, nonce, authTime, sid} JSON.
	 *
	 * nonce/authTime 을 함께 싣는 이유..
	 *      두 값은 authorize 시점(이 서비스)에만 알 수 있는데 정작 필요한 곳은 id token 을 만드는 token 서비스다.
	 *      client 가 token 요청에 실어 보내게 하면 조작 가능하므로, 서버끼리만 오가는 code 레코드에 담아 전달한다.
	 *      (표준은 id token 에 nonce/auth_time 이 규칙대로 담길 것을 요구할 뿐 나르는 방법은 규정하지 않는다)
	 *      sid 도 같은 이유로 여기 담는다 — OP 세션 식별자는 로그인한 이 서비스만 알고, 필요한 곳은 token 이다.
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

	public String issue(String clientId, String redirectUri, String scope, String sub, String codeChallenge,
			String nonce, long authTime, String sid) {
		String code = UUID.randomUUID().toString().replace("-", "");
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("clientId", clientId);
		data.put("redirectUri", redirectUri);
		data.put("scope", scope);
		data.put("sub", sub);
		data.put("codeChallenge", codeChallenge);
		data.put("nonce", nonce);
		data.put("authTime", authTime);
		data.put("sid", sid);
		try {
			redisTemplate.opsForValue().set(KEY_PREFIX + code, objectMapper.writeValueAsString(data), Duration.ofSeconds(ttlSeconds));
		} catch (Exception e) {
			throw new IllegalStateException("failed to store authorization code", e);
		}
		return code;
	}
}
