package dev.starryeye.audit;

import dev.starryeye.audit.jpa.RefreshTokenEventEntity;
import dev.starryeye.audit.jpa.RefreshTokenEventEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class RefreshTokenEventRecorder {

	/**
	 * refresh 계열 사건 한 줄을 남긴다. 같은 eventId 가 다시 오면 아무것도 하지 않는다(멱등).
	 *
	 * 주의. 멱등의 알고리즘과 그 함정(@Transactional 을 두지 않는 것 포함)은 IdempotentInsert 에 있고 AuditRecorder 와
	 *      같다. 같은 eventId 인데 내용이 다른 편지가 조용히 흡수되는 것도 같다(AuditRecorder 참고).
	 *
	 * 주의. recorded_at 은 이 메서드가 호출된 시각이다. 발행자가 적은 사건 시각(occurred_at)과 다르다.
	 *
	 * @return 새로 기록했으면 true, 이미 있어 흡수했으면 false
	 */

	private final RefreshTokenEventEntityRepository repository;

	public boolean record(RefreshTokenEventRecord record) {
		return IdempotentInsert.insertOnce(record.eventId(), repository::existsByEventId,
				() -> repository.save(RefreshTokenEventEntity.builder()
						.eventId(record.eventId())
						.eventType(record.eventType())
						.kind(record.kind())
						.occurredAt(record.occurredAt())
						.recordedAt(Instant.now())
						.sub(record.sub())
						.clientId(record.clientId())
						.familyId(record.familyId())
						.sid(record.sid())
						.reason(record.reason())
						.revokedTokens(record.revokedTokens())
						.activeTokensRevoked(record.activeTokensRevoked())
						.expiresAt(record.expiresAt())
						.familyExpiresAt(record.familyExpiresAt())
						.payload(record.payload())
						.build()));
	}
}
