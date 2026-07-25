package dev.starryeye.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
public class PendingAuthorizationStore {

	/**
	 * 동의 화면을 거치는 동안 진행 중인 인가 요청을 서버에 보관한다.
	 *      화면에는 불투명한 pendingId 만 내보내고 client_id/redirect_uri/scope 는 서버에만 둔다.
	 *      -> 폼 hidden 으로 흘리면 사용자가 scope 를 올리거나 redirect_uri 를 바꿔치기할 수 있다.
	 *
	 * 주의. 표준이 정한 방식이 아니라 구현 선택이다. OIDC 는 동의 화면 상태 유지 방법을 규정하지 않는다.
	 *      (같은 패턴이 널리 쓰인다.. spring authorization server 는 진행 중 authorization 을 저장소에 두고 내부 state 로 조회하고,
	 *       keycloak 은 authentication session 에 두고 불투명한 tab id 를 노출한다)
	 */

	private static final String KEY_PREFIX = "auth:pending:";

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final long ttlSeconds;

	public PendingAuthorizationStore(
			StringRedisTemplate redisTemplate,
			ObjectMapper objectMapper,
			@Value("${my.pending-authorization-ttl-seconds}") long ttlSeconds
	) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		this.ttlSeconds = ttlSeconds;
	}

	public String save(PendingAuthorization pending) {
		String pendingId = UUID.randomUUID().toString().replace("-", "");
		try {
			redisTemplate.opsForValue().set(KEY_PREFIX + pendingId,
					objectMapper.writeValueAsString(pending), Duration.ofSeconds(ttlSeconds));
		} catch (Exception e) {
			throw new IllegalStateException("failed to store pending authorization", e);
		}
		return pendingId;
	}

	public Optional<PendingAuthorization> consume(String pendingId) {
		String json = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + pendingId); // 원자적 조회+삭제(1회용)
		if (json == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(objectMapper.readValue(json, PendingAuthorization.class));
		} catch (Exception e) {
			return Optional.empty();
		}
	}
}
