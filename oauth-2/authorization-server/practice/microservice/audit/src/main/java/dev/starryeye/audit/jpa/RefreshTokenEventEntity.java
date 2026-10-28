package dev.starryeye.audit.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "refresh_token_events",
		uniqueConstraints = @UniqueConstraint(name = "uk_refresh_token_events_event_id", columnNames = "event_id"),
		indexes = @Index(name = "idx_refresh_token_events_sub", columnList = "sub"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenEventEntity {

	/**
	 * refresh token 계열(family)에서 일어난 사건 한 줄(ISSUED · ROTATED · REVOKED). AuditEventEntity 와 마찬가지로 이
	 *      서비스의 운영 코드는 행을 INSERT 만 하고 고치거나 지우지 않는다(append-only 로 쓴다). 강제되는 것은
	 *      아니다 — AuditEventEntity 의 같은 문단 참고.
	 *
	 * 주의. audit_events 를 넓히지 않고 테이블을 따로 둔다. audit_events 의 주체는 세션이라 subject_sid 가 not null
	 *      인 사실인데, 이 사건들의 주체는 계열이고 계열의 sid 는 null 일 수 있다(refresh_tokens.sid 가 nullable).
	 *      같은 테이블에 넣으면 그 not null 이 무너진다.
	 *
	 * 주의. sub · client_id · family_id 는 발행자(token-state)가 소유한 사실이다. audit_events 의 reported_sub 는 발행자가
	 *      "그렇다고 보고한 값"일 뿐이지만(AuditRecord 참고), 여기서는 발행자가 자기 refresh_tokens 행에서 읽어 싣는
	 *      값이다. sid 는 null 일 수 있어 nullable 이고, reason · revoked_tokens · active_tokens_revoked 는 REVOKED 만,
	 *      expires_at 은 ISSUED 와 ROTATED, family_expires_at 은 ISSUED 만 채운다. 채우지 않은 값은 NULL 이다.
	 *
	 * 주의. kind 는 문자열 그대로 담고 어떤 값인지는 제한하지 않는다. 같은 v1 계약에 종류가 더해져도 받아 적힌다.
	 *      길이는 컬럼이 30으로 제한한다(length = 30). 컬럼 길이가 강제되는 환경(strict SQL 모드)에서 더 긴 kind 는
	 *      INSERT 가 실패하고, 기록기(IdempotentInsert)는 행이 없는 실패를 흡수하지 않고 다시 던지므로 재시도한 뒤 감사
	 *      DLT 로 간다. 코드에서 나온 결론이고 이를 고정하는 테스트는 없다. 길이가 정해진 다른 컬럼도 같다. 이 테이블을
	 *      읽는 쪽은 모르는 kind 를 계열의 상태 판정에 쓰지 말아야 한다.
	 *
	 * 주의. event_id 의 UNIQUE 가 멱등의 근거다(AuditEventEntity 와 같은 이유). sub 인덱스는 사용자 한 명의 사건을
	 *      모아 읽는 경로를 위한 것이다.
	 *
	 * 주의. occurred_at 은 발행자가 기록한 사건 시각, recorded_at 은 감사가 받아 적은 시각이다. 둘의 차이가 전달
	 *      지연이다.
	 *
	 * 주의. payload 는 원문 JSON 통째라서 AuditEventEntity.payload 와 같은 규칙으로 @Lob 에 length 를 16_777_215 로
	 *      명시한다(MySQL 에서 mediumtext). length 를 주지 않으면 tinytext(255바이트)가 된다 — 이유와 h2 테스트가 못
	 *      가리는 것은 AuditEventEntity 의 같은 문단에 있다.
	 */

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false, length = 36)
	private String eventId;

	@Column(name = "event_type", nullable = false, length = 255)
	private String eventType;

	@Column(name = "kind", nullable = false, length = 30)
	private String kind;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "recorded_at", nullable = false)
	private Instant recordedAt;

	@Column(name = "sub", nullable = false, length = 255)
	private String sub;

	@Column(name = "client_id", nullable = false, length = 255)
	private String clientId;

	@Column(name = "family_id", nullable = false, length = 36)
	private String familyId;

	@Column(name = "sid", length = 64)
	private String sid;

	@Column(name = "reason", length = 30)
	private String reason;

	@Column(name = "revoked_tokens")
	private Integer revokedTokens;

	@Column(name = "active_tokens_revoked")
	private Integer activeTokensRevoked;

	@Column(name = "expires_at")
	private Instant expiresAt;

	@Column(name = "family_expires_at")
	private Instant familyExpiresAt;

	@Lob
	@Column(nullable = false, length = 16_777_215)
	private String payload;

	@Builder
	private RefreshTokenEventEntity(String eventId, String eventType, String kind, Instant occurredAt,
			Instant recordedAt, String sub, String clientId, String familyId, String sid, String reason,
			Integer revokedTokens, Integer activeTokensRevoked, Instant expiresAt, Instant familyExpiresAt,
			String payload) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.kind = kind;
		this.occurredAt = occurredAt;
		this.recordedAt = recordedAt;
		this.sub = sub;
		this.clientId = clientId;
		this.familyId = familyId;
		this.sid = sid;
		this.reason = reason;
		this.revokedTokens = revokedTokens;
		this.activeTokensRevoked = activeTokensRevoked;
		this.expiresAt = expiresAt;
		this.familyExpiresAt = familyExpiresAt;
		this.payload = payload;
	}
}
