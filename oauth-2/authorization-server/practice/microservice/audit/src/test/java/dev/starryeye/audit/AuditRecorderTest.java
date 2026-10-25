package dev.starryeye.audit;

import dev.starryeye.audit.jpa.AuditEventEntity;
import dev.starryeye.audit.jpa.AuditEventEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:audit-recorder;DB_CLOSE_DELAY=-1;MODE=MySQL")
class AuditRecorderTest {

	/**
	 * 감사 레코드가 이벤트의 사실을 그대로 받아 적는지, 같은 이벤트를 두 번 받아도 한 줄인지,
	 *      그리고 흡수하면 안 되는 위반은 흡수하지 않는지를 실제 DB 제약으로 확인한다.
	 *
	 * 주의. datasource url 을 이 클래스 전용으로 준다. 이 저장소의 테스트는 이름 있는 h2 인메모리 DB 를
	 *      여러 @SpringBootTest 컨텍스트가 공유하는 함정을 슬라이스 7에서 겪었다(프로퍼티가 달라 컨텍스트
	 *      캐시가 갈려도 같은 이름이면 같은 테이블이다).
	 */

	private static final String TOPIC = "oidc.session.logged-out.v1";

	@Autowired
	private AuditRecorder recorder;

	@Autowired
	private AuditEventEntityRepository repository;

	@BeforeEach
	void clean() {
		repository.deleteAll();
	}

	@Test
	@DisplayName("이벤트의 사실을 공통 머리와 원문으로 받아 적는다")
	void recordsFacts() {
		Instant occurredAt = Instant.parse("2026-09-28T01:02:03.456Z");
		String payload = "{\"eventId\":\"E-1\",\"sid\":\"SID-A\",\"sub\":\"user-sub-0001\",\"occurredAt\":\"2026-09-28T01:02:03.456Z\"}";

		boolean recorded = recorder.record(new AuditRecord("E-1", TOPIC, occurredAt, "SID-A", "user-sub-0001", payload));

		assertThat(recorded).isTrue();
		List<AuditEventEntity> rows = repository.findAll();
		assertThat(rows).hasSize(1);
		AuditEventEntity row = rows.get(0);
		assertThat(row.getEventId()).isEqualTo("E-1");
		assertThat(row.getEventType()).isEqualTo(TOPIC);
		assertThat(row.getOccurredAt()).isEqualTo(occurredAt);
		assertThat(row.getSubjectSid()).isEqualTo("SID-A");
		assertThat(row.getReportedSub()).isEqualTo("user-sub-0001");
		assertThat(row.getPayload()).isEqualTo(payload);
		assertThat(row.getRecordedAt()).isNotNull();
	}

	/**
	 * 100,000바이트는 text(64KB)의 한도를 넘기려고 고른 크기다.
	 *
	 * 주의. 이 테스트는 h2 에서는 매핑 회귀를 물지 못한다. AuditEventEntity.payload 의 length 를 기본값으로 되돌려도
	 *      통과하는 것을 확인했다 — h2 는 두 매핑 모두 payload 를 CHARACTER LARGE OBJECT(최대 길이 Long.MAX_VALUE)로
	 *      만들어 길이를 강제하지 않는다. 그래서 여기서 확인되는 것은 기록기와 엔티티를 거쳐 큰 값이 잘리지 않고
	 *      오가는 경로까지다. 컬럼이 실제로 충분히 큰지는 실제 MySQL 에서만 보이고(SHOW CREATE TABLE 의 mediumtext,
	 *      롤백되는 트랜잭션 안에서 100,000바이트와 1MiB 를 SQL 로 직접 INSERT), 이 테스트는 그 확인을 대신하지 않는다.
	 */
	@Test
	@DisplayName("64KB 를 넘는 payload 도 잘리지 않고 그대로 기록된다")
	void recordsPayloadLargerThan64KbIntact() {
		String payload = "{\"eventId\":\"E-5\",\"padding\":\"" + "x".repeat(100_000) + "\"}";

		boolean recorded = recorder.record(new AuditRecord("E-5", TOPIC, Instant.now(), "SID-A", "user-sub-0001", payload));

		assertThat(recorded).isTrue();
		AuditEventEntity row = repository.findAll().get(0);
		assertThat(row.getPayload().length()).isEqualTo(payload.length());
		assertThat(row.getPayload()).isEqualTo(payload);
	}

	@Test
	@DisplayName("같은 eventId 를 두 번 받으면 한 줄이다")
	void sameEventIdIsRecordedOnce() {
		AuditRecord record = new AuditRecord("E-2", TOPIC, Instant.now(), "SID-A", "user-sub-0001", "{}");

		assertThat(recorder.record(record)).isTrue();
		assertThat(recorder.record(record)).isFalse();

		assertThat(repository.findAll()).hasSize(1);
	}

	@Test
	@DisplayName("sub 가 없는 이벤트도 기록된다 — reported_sub 는 NULL 이다(Review Focus 1)")
	void recordsNullReportedSub() {
		boolean recorded = recorder.record(new AuditRecord("E-3", TOPIC, Instant.now(), "SID-NO-RP", null, "{}"));

		assertThat(recorded).isTrue();
		assertThat(repository.findAll().get(0).getReportedSub()).isNull();
	}

	@Test
	@DisplayName("sid 가 빠진 기형 이벤트는 흡수되지 않고 실패한다 — 조용히 흡수하면 감사가 기록을 잃는다")
	void missingSidIsNotAbsorbed() {
		AuditRecord record = new AuditRecord("E-4", TOPIC, Instant.now(), null, "user-sub-0001", "{}");

		assertThatThrownBy(() -> recorder.record(record))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(repository.findAll()).isEmpty();
	}

	@Test
	@DisplayName("eventId 가 빠진 편지는 흡수되지 않고 실패한다 — 없는 값끼리를 같은 이벤트로 치면 안 된다(Review Focus 5)")
	void missingEventIdIsNotAbsorbed() {
		AuditRecord record = new AuditRecord(null, TOPIC, Instant.now(), "SID-A", "user-sub-0001", "{}");

		assertThatThrownBy(() -> recorder.record(record))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThat(repository.findAll()).isEmpty();
	}
}
