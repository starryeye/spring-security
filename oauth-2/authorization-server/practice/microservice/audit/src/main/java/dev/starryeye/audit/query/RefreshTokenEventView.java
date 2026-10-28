package dev.starryeye.audit.query;

import dev.starryeye.audit.jpa.RefreshTokenEventEntity;

import java.time.Instant;

/**
 * 계열 안의 사건 한 줄. refresh_token_events 의 행에서 사건마다 다른 값을 옮긴다. sub 는 응답에, familyId ·
 *      clientId · sid 는 계열 요약에 한 번만 싣고, payload 원문과 event_type 은 싣지 않는다.
 *
 * 주의. 응답 JSON 의 키는 이 record 의 필드 이름 그대로다. 사건 종류마다 채우는 필드가 달라(RefreshTokenEventEntity
 *      참고) 값이 없는 필드가 많은데, 그 키도 빠지지 않고 null 로 나간다 — Jackson 의 기본 포함 규칙이고 이 서비스는
 *      그것을 바꾸지 않는다.
 *
 * 주의. kind 는 저장된 문자열 그대로다. 모르는 kind 도 이 목록에는 보인다.
 */
public record RefreshTokenEventView(
		String eventId,
		String kind,
		Instant occurredAt,
		Instant recordedAt,
		Instant expiresAt,
		Instant familyExpiresAt,
		String reason,
		Integer revokedTokens,
		Integer activeTokensRevoked
) {

	public static RefreshTokenEventView from(RefreshTokenEventEntity entity) {
		return new RefreshTokenEventView(
				entity.getEventId(),
				entity.getKind(),
				entity.getOccurredAt(),
				entity.getRecordedAt(),
				entity.getExpiresAt(),
				entity.getFamilyExpiresAt(),
				entity.getReason(),
				entity.getRevokedTokens(),
				entity.getActiveTokensRevoked());
	}
}
