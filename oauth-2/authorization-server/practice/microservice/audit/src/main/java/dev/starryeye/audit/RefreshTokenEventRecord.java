package dev.starryeye.audit;

import java.time.Instant;

/**
 * refresh 계열 사건 한 줄에 들어갈 값. 소비자가 이벤트에서 뽑아 만든다.
 *
 * 주의. eventType 은 페이로드가 아니라 수신한 토픽 이름이고, kind(ISSUED · ROTATED · REVOKED)는 페이로드의 값이다.
 *      둘은 다른 축이다 — 토픽은 계약(버전)을, kind 는 그 계약 안의 사건 종류를 말한다.
 *
 * 주의. 이 record 는 값을 검사하지 않는다. 필수 값을 거르는 것은 소비자이고, 필수는 eventId · kind · occurredAt ·
 *      familyId · sub · clientId 다(테이블에서도 not null). 나머지(sid · expiresAt · familyExpiresAt · reason ·
 *      revokedTokens · activeTokensRevoked)는 사건 종류에 따라 비어 있을 수 있다.
 */
public record RefreshTokenEventRecord(
		String eventId,
		String eventType,
		String kind,
		Instant occurredAt,
		String familyId,
		String sub,
		String clientId,
		String sid,
		Instant expiresAt,
		Instant familyExpiresAt,
		String reason,
		Integer revokedTokens,
		Integer activeTokensRevoked,
		String payload
) {
}
