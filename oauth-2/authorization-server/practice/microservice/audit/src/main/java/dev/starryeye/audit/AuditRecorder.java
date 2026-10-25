package dev.starryeye.audit;

import dev.starryeye.audit.jpa.AuditEventEntity;
import dev.starryeye.audit.jpa.AuditEventEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuditRecorder {

	/**
	 * 감사 레코드 한 줄을 남긴다. 같은 eventId 가 다시 오면 아무것도 하지 않는다(멱등).
	 *
	 * 주의. 선조회(existsByEventId)만으로는 멱등이 아니다. 같은 eventId 가 거의 동시에 두 번 도착하면 둘 다
	 *      "없음"을 보고 둘 다 INSERT 를 시도하고, 진 쪽이 uk_audit_events_event_id 위반을 맞는다. 그 위반을
	 *      흡수해야 호출자(소비자) 관점에서도 멱등이다 — 중복은 예외가 아니라 false 여야 한다. 흡수하지 않으면
	 *      멀쩡한 중복 전달이 실패로 취급돼 불필요한 에러 로그와 재시도를 낸다. 다만 DLT 까지는 가지 않는다 — 이
	 *      위반은 상대 전달이 그 행을 커밋한 뒤에 나므로, 소비자가 재시도하면 선조회가 그 행을 보고 false 로 끝난다.
	 *      재시도를 소진해도 안 풀리는 것은 아래의 다시 던지는 갈래(다른 제약 위반)다.
	 *
	 * 주의. 다만 DataIntegrityViolationException 을 무조건 흡수하면 안 된다. 이 예외는 유니크 위반뿐 아니라
	 *      not null 위반에서도 난다(sid 나 eventId 가 빠진 기형 이벤트). 무조건 흡수하면 기록에 실패한 이벤트가
	 *      성공으로 넘어가 감사가 그 기록을 조용히 잃는다. 그래서 잡은 뒤 existsByEventId 로 재확인한다 —
	 *      행이 실제로 있으면(다른 전달이 먼저 넣었으면) 흡수하고, 없으면 다시 던진다.
	 *
	 * 주의. 이 메서드에는 @Transactional 을 두지 않는다. 감싸면 IDENTITY 채번이 save 시점에 즉시 flush 하다
	 *      실패하면서 그 바깥 트랜잭션을 rollback-only 로 오염시켜, 예외를 잡아도 커밋 시점에
	 *      UnexpectedRollbackException 이 새로 터진다. existsByEventId 와 save 를 각각 레포지토리 자신의
	 *      트랜잭션으로 두어야 catch 가 실제로 유효하다. session 의 SessionService.register 와 같은 이유·같은 모양이다.
	 *
	 * 주의. 같은 eventId 인데 payload 가 다른 편지가 오면 조용히 흡수된다. 정상 경로에서는 불가능하고(outbox 는
	 *      같은 행을 재전송한다), 일어난다면 발행자가 eventId 를 재사용한 버그다. 어느 쪽이 진짜인지 판정할
	 *      근거가 감사에게 없어 비교하지 않는다(스펙 4절).
	 *
	 * @return 새로 기록했으면 true, 이미 있어 흡수했으면 false
	 */

	private final AuditEventEntityRepository repository;

	public boolean record(AuditRecord record) {
		if (repository.existsByEventId(record.eventId())) {
			return false;
		}
		try {
			repository.save(AuditEventEntity.builder()
					.eventId(record.eventId())
					.eventType(record.eventType())
					.occurredAt(record.occurredAt())
					.recordedAt(Instant.now())
					.subjectSid(record.subjectSid())
					.reportedSub(record.reportedSub())
					.payload(record.payload())
					.build());
			return true;
		} catch (DataIntegrityViolationException e) {
			// 유니크 위반으로 진 쪽이면 다른 전달이 같은 eventId 행을 먼저 만든 것이다 — 원하던 결과가 이미 있으므로 흡수한다.
			// 없으면(not null 등 다른 제약 위반) 기록이 진짜로 실패한 것이므로 다시 던진다.
			if (!repository.existsByEventId(record.eventId())) {
				throw e;
			}
			return false;
		}
	}
}
