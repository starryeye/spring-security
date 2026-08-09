package dev.starryeye.session.jpa;

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
	 * 아직 Kafka 로 나가지 않은 이벤트를 담는다.
	 *
	 * 주의. 이 테이블의 존재 이유는 원자성 하나다. 상태 변경(oidc_sessions 삭제)과 이벤트 기록이 같은
	 *      트랜잭션에 들어가야 "로그아웃은 됐는데 편지가 안 갔다"와 "편지는 갔는데 로그아웃이 안 됐다"가
	 *      둘 다 불가능해진다. DB 와 Kafka 는 서로 다른 시스템이라 직접 묶을 방법이 없다.
	 *
	 * 주의. published_at 이 null 인 행이 미발행이다. 발행 후 표시 직전에 죽으면 다음 주기에 다시 보내므로
	 *      전달은 at-least-once 다. 소비자의 폐기가 조건부 갱신이라 멱등이므로 그대로 둔다.
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
	@Column(nullable = false)
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
