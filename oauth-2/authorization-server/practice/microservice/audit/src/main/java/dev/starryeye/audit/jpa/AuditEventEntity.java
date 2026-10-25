package dev.starryeye.audit.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
@Table(name = "audit_events",
		uniqueConstraints = @UniqueConstraint(name = "uk_audit_events_event_id", columnNames = "event_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEventEntity {

	/**
	 * 감사 레코드 한 줄. append-only 다 — 한 번 쓰면 고치지도 지우지도 않는다.
	 *
	 * 주의. 모든 이벤트가 공유하는 것만 컬럼으로 뽑고 나머지는 payload 원문으로 보관한다. 이벤트 타입이
	 *      늘어도(후속 슬라이스) 스키마가 바뀌지 않고, 발행자가 필드를 더해도 손실 없이 받아 적는다.
	 *
	 * 주의. event_id 의 UNIQUE 가 멱등의 근거다. append-only 소비자는 같은 이벤트를 두 번 받으면 그대로
	 *      두 줄이 되므로(outbox 재전송, 오프셋 커밋 전 재기동), 폐기처럼 조건부 갱신으로 멱등이 공짜가
	 *      되지 않는다.
	 *
	 * 주의. occurred_at 은 발행자가 기록한 사건 시각, recorded_at 은 감사가 받아 적은 시각이다. 둘의 차이가
	 *      전달 지연이다.
	 *
	 * 주의. subject_sid 는 지금 not null 이다 — 로그아웃 이벤트의 sid 는 항상 있다. refresh 계열 이벤트를
	 *      받게 되면 그 원천(refresh_tokens.sid)이 nullable 이라 이 제약을 완화해야 한다(스펙 3절). 미리 열어두지
	 *      않는 것은 도달 불가능한 상태를 허용하지 않기 위해서다.
	 *
	 * 주의. payload 는 @Lob 이지만 @Column 에 length 를 주지 않아 MySQL 에서 longtext 가 아니라 tinytext(255바이트)가
	 *      된다. @Lob String 의 DDL 타입이 length 에 따라 달라지고 기본값이 255 이기 때문이다 — 실제 MySQL 8 에서
	 *      SHOW CREATE TABLE 로 tinytext 를 확인했고, length 를 10000 으로 주면 text 가 되는 것도 실험으로 확인했다.
	 *      255바이트를 넘는 값을 SQL 로 직접 INSERT 하면 MySQL 이 ERROR 1406(Data too long)으로 거부한다. 위
	 *      "손실 없이 받아 적는다"는 payload 가 그 한도 안일 때만 성립한다. 한도를 늘리려면 length 나 columnDefinition 을
	 *      명시해야 한다.
	 */

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false, length = 36)
	private String eventId;

	@Column(name = "event_type", nullable = false, length = 255)
	private String eventType;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Column(name = "recorded_at", nullable = false)
	private Instant recordedAt;

	@Column(name = "subject_sid", nullable = false, length = 64)
	private String subjectSid;

	@Column(name = "reported_sub")
	private String reportedSub;

	@Lob
	@Column(nullable = false)
	private String payload;

	@Builder
	private AuditEventEntity(String eventId, String eventType, Instant occurredAt, Instant recordedAt,
			String subjectSid, String reportedSub, String payload) {
		this.eventId = eventId;
		this.eventType = eventType;
		this.occurredAt = occurredAt;
		this.recordedAt = recordedAt;
		this.subjectSid = subjectSid;
		this.reportedSub = reportedSub;
		this.payload = payload;
	}
}
