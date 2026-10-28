package dev.starryeye.audit;

import dev.starryeye.audit.jpa.AuditEventEntity;
import dev.starryeye.audit.jpa.AuditEventEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuditRecorder {

	/**
	 * 감사 레코드 한 줄을 남긴다. 같은 eventId 가 다시 오면 아무것도 하지 않는다(멱등).
	 *
	 * 주의. 멱등의 알고리즘(선조회 → save → 유니크 위반 흡수·재확인)과 그 함정(선조회만으로는 멱등이 아니다, 위반을
	 *      무조건 흡수하면 안 된다, @Transactional 을 두지 않는다)은 IdempotentInsert 에 있다. 같은 갈래를
	 *      AuditRecorderBranchTest 가 이 클래스를 통해 고정한다.
	 *
	 * 주의. 같은 eventId 인데 payload 가 다른 편지가 오면 조용히 흡수된다. 정상 경로에서는 불가능하고(outbox 는
	 *      같은 행을 재전송한다), 일어난다면 발행자가 eventId 를 재사용한 버그다. 어느 쪽이 진짜인지 판정할
	 *      근거가 감사에게 없어 비교하지 않는다(스펙 4절).
	 *
	 * @return 새로 기록했으면 true, 이미 있어 흡수했으면 false
	 */

	private final AuditEventEntityRepository repository;

	public boolean record(AuditRecord record) {
		return IdempotentInsert.insertOnce(record.eventId(), repository::existsByEventId,
				() -> repository.save(AuditEventEntity.builder()
						.eventId(record.eventId())
						.eventType(record.eventType())
						.occurredAt(record.occurredAt())
						.recordedAt(Instant.now())
						.subjectSid(record.subjectSid())
						.reportedSub(record.reportedSub())
						.payload(record.payload())
						.build()));
	}
}
