package dev.starryeye.token_state.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "outbox", indexes = @Index(name = "idx_outbox_unpublished", columnList = "published_at, id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEntity {

	/**
	 * 아직 Kafka 로 나가지 않은 refresh token 계열의 사건을 담는다.
	 *
	 * 주의. 이 테이블의 존재 이유는 원자성 하나다. refresh_tokens 의 상태 변경과 사건 기록이 같은 트랜잭션에
	 *      들어가야 "토큰 상태는 바뀌었는데 감사 기록이 없다"와 "감사 기록은 있는데 상태 변경은 롤백됐다"가 둘 다
	 *      불가능해진다. DB 와 Kafka 는 서로 다른 시스템이라 직접 묶을 방법이 없다.
	 *
	 * 주의. published_at 이 null 인 행이 미발행이다. 발행 후 표시 직전에 죽으면 다음 주기에 같은 행을 다시
	 *      보내므로 전달은 at-least-once 다. 다시 보내는 행은 같은 payload 를 싣고, 그 안의 eventId 도 같다 —
	 *      중복은 소비자가 eventId 로 걸러야 한다.
	 *
	 * 주의. payload 는 @Lob 만으로 충분하지 않아 @Column 의 length 를 16_777_215 로 명시한다. MySQL 에서
	 *      @Lob String 의 DDL 타입은 length 로 정해지고, length 를 주지 않으면 tinytext(255바이트)가 된다.
	 *      ISSUED 의 payload 만 해도 UUID 둘과 ISO-8601 시각 셋이 들어가 그보다 길다. 16_777_215 는
	 *      mediumtext 로 이어지는 값이며, 이 규칙과 근거는 audit 의 AuditEventEntity.payload 주석에 같은 형태로
	 *      적혀 있다. h2 는 length 값을 구분하지 못한다(그 주석이 적은 대로) — 컬럼 크기는 실제 MySQL 에서만
	 *      확인된다.
	 */

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "event_id", nullable = false, length = 36)
	private String eventId;

	@Column(nullable = false, length = 100)
	private String topic;

	@Column(name = "partition_key", nullable = false, length = 64)
	private String partitionKey;

	@Lob
	@Column(nullable = false, length = 16_777_215)
	private String payload;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "published_at")
	private Instant publishedAt;

	@Builder
	private OutboxEntity(String eventId, String topic, String partitionKey, String payload, Instant createdAt) {
		this.eventId = eventId;
		this.topic = topic;
		this.partitionKey = partitionKey;
		this.payload = payload;
		this.createdAt = createdAt;
	}

	public void markPublished(Instant at) {
		this.publishedAt = at;
	}
}
