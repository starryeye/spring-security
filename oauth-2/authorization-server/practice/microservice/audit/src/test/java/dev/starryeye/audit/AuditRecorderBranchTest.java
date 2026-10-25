package dev.starryeye.audit;

import dev.starryeye.audit.jpa.AuditEventEntityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditRecorderBranchTest {

	/**
	 * catch 경로의 두 갈래를 스프링 없이 고정한다.
	 *
	 * 주의. 같은 이벤트를 순차로 두 번 넣는 통합 테스트는 두 번째가 선조회(existsByEventId)에서 끝나
	 *      catch 경로를 한 번도 타지 않는다. catch 의 흡수 갈래가 실제로 쓰이는 것은 두 전달이 거의 동시에 도착해
	 *      둘 다 선조회를 통과한 뒤 한쪽이 유니크 위반을 맞는 경우뿐이라, 통합 테스트로는 재현하기 어렵다.
	 *      그래서 선조회 결과와 save 의 예외를 직접 지정해 두 갈래를 각각 고정한다.
	 *
	 * 주의. 다시 던짐 갈래는 유니크 위반만이 아니라 not null 같은 다른 제약 위반으로도 들어가므로 AuditRecorderTest 의
	 *      missingSid·missingEventId 도 그 갈래를 행사한다. 여기서는 위반의 종류와 무관하게 "재확인에 행이 없다"는
	 *      조건만으로 던지는지를 고정한다.
	 */

	private static final AuditRecord RECORD = new AuditRecord(
			"E-1", "oidc.session.logged-out.v1", Instant.parse("2026-09-28T00:00:00Z"),
			"SID-A", "user-sub-0001", "{\"eventId\":\"E-1\"}");

	@Test
	@DisplayName("선조회는 통과했는데 저장이 위반을 맞고, 재확인하니 행이 있다 — 다른 전달이 먼저 넣은 것이므로 흡수한다")
	void absorbsWhenRowAppearedConcurrently() {
		AuditEventEntityRepository repository = mock(AuditEventEntityRepository.class);
		when(repository.existsByEventId("E-1")).thenReturn(false, true);
		when(repository.save(any())).thenThrow(new DataIntegrityViolationException("uk_audit_events_event_id"));

		boolean recorded = new AuditRecorder(repository).record(RECORD);

		assertThat(recorded).isFalse();
	}

	@Test
	@DisplayName("저장이 위반을 맞았는데 재확인해도 행이 없다 — 다른 제약 위반이므로 다시 던진다")
	void rethrowsWhenRowStillAbsent() {
		AuditEventEntityRepository repository = mock(AuditEventEntityRepository.class);
		when(repository.existsByEventId("E-1")).thenReturn(false, false);
		when(repository.save(any())).thenThrow(new DataIntegrityViolationException("not-null subject_sid"));

		assertThatThrownBy(() -> new AuditRecorder(repository).record(RECORD))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
