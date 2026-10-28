package dev.starryeye.token_state.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.token_state.jpa.OutboxEntity;
import dev.starryeye.token_state.jpa.OutboxEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RefreshTokenEventPublisher {

	/**
	 * refresh token 계열의 사건을 outbox 에 기록한다. Kafka 를 직접 부르지 않는다.
	 *
	 * 주의. 호출자(RefreshTokenService)의 트랜잭션에 참여한다. refresh_tokens 의 변경과 이 INSERT 가 함께
	 *      커밋되거나 함께 롤백되므로, 상태 변경과 사건 기록 사이의 틈이 사라진다. Kafka 로 옮기는 일은
	 *      OutboxPublisher 가 별도 주기로 하고, 실패해도 행이 DB 에 남아 다음 주기에 다시 시도된다.
	 *      이 클래스의 메서드는 트랜잭션 없이 부르면 outbox 행만 따로 커밋된다 — 반드시 상태를 바꾸는
	 *      트랜잭션 안에서 부른다.
	 *
	 * 주의. 파티션 키는 familyId 다. 한 계열의 사건이 한 파티션에서 순서대로 소비된다. 이 순서는 outbox 에 쓰인
	 *      순서(id)다. occurredAt 은 호출자가 넘긴 시각이라 둘이 같다고 가정하지 않는다 — 사건을 시각순으로
	 *      놓아야 하면 occurredAt 으로 정렬한다. 계열이 다르면 파티션도 달라 서로의 순서는 정해져 있지 않다.
	 *
	 * 주의. 사건에는 토큰 원문도 해시도 싣지 않는다. 이 클래스가 받는 RefreshTokenEntity 에서 읽는 것은
	 *      계열 · 주체 · client · 세션 · 만료 시각뿐이다.
	 */

	public static final String LIFECYCLE_TOPIC = "oidc.refresh-token.lifecycle.v1";

	private final OutboxEntityRepository outboxRepository;
	private final ObjectMapper objectMapper;

	/**
	 * 새 계열의 첫 토큰이 발급됐다. expiresAt 은 이 토큰의 만료, familyExpiresAt 은 계열의 절대 상한이다.
	 */
	public void issued(RefreshTokenEntity issued, Instant at) {
		record(new RefreshTokenLifecycleEvent(
				UUID.randomUUID().toString(), RefreshTokenLifecycleEvent.ISSUED, at,
				issued.getFamilyId(), issued.getSub(), issued.getClientId(), issued.getSid(),
				issued.getExpiresAt(), issued.getFamilyExpiresAt(),
				null, null, null));
	}

	/**
	 * 토큰이 회전해 같은 계열에 새 토큰이 생겼다. 새 토큰의 만료(expiresAt)만 싣는다 — 계열 상한은 회전으로
	 *      바뀌지 않고 ISSUED 가 이미 알렸다.
	 */
	public void rotated(RefreshTokenEntity rotated, Instant at) {
		record(new RefreshTokenLifecycleEvent(
				UUID.randomUUID().toString(), RefreshTokenLifecycleEvent.ROTATED, at,
				rotated.getFamilyId(), rotated.getSub(), rotated.getClientId(), rotated.getSid(),
				rotated.getExpiresAt(), null,
				null, null, null));
	}

	/**
	 * 계열의 토큰이 폐기됐다. member 는 그 계열의 아무 행이나 되고, 계열 · 주체 · client · 세션은 거기서 읽는다.
	 *      revokedTokens 는 이번에 실제로 REVOKED 로 바뀐 행 수, activeTokensRevoked 는 그중 바뀌기 직전에
	 *      ACTIVE 였던 행 수다. reason 은 refresh_tokens.revoked_reason 과 같은 문자열을 받는다.
	 */
	public void revoked(RefreshTokenEntity member, Instant at, String reason, int revokedTokens, int activeTokensRevoked) {
		record(new RefreshTokenLifecycleEvent(
				UUID.randomUUID().toString(), RefreshTokenLifecycleEvent.REVOKED, at,
				member.getFamilyId(), member.getSub(), member.getClientId(), member.getSid(),
				null, null,
				reason, revokedTokens, activeTokensRevoked));
	}

	private void record(RefreshTokenLifecycleEvent event) {
		String payload;
		try {
			payload = objectMapper.writeValueAsString(event);
		} catch (JsonProcessingException e) {
			// 직렬화 실패는 재시도로 낫지 않는다. 트랜잭션을 죽여 상태 변경 자체를 실패시킨다 —
			// 기록하지 못한 사건을 성공한 것처럼 커밋하면 그 사실이 영원히 사라진다.
			throw new IllegalStateException("failed to serialize refresh token event: kind=" + event.kind()
					+ " familyId=" + event.familyId(), e);
		}

		outboxRepository.save(OutboxEntity.builder()
				.eventId(event.eventId())
				.topic(LIFECYCLE_TOPIC)
				.partitionKey(event.familyId())
				.payload(payload)
				.createdAt(event.occurredAt())
				.build());
	}
}
