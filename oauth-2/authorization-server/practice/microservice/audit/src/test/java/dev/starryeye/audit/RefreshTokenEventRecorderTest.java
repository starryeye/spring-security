package dev.starryeye.audit;

import dev.starryeye.audit.jpa.RefreshTokenEventEntity;
import dev.starryeye.audit.jpa.RefreshTokenEventEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:audit-refresh-recorder;DB_CLOSE_DELAY=-1;MODE=MySQL",
		"spring.kafka.listener.auto-startup=false"   // 리스너 컨테이너만 끈다. 실제 브로커가 떠 있어도 refresh 계열 사건을 받아 적지 않게
})
class RefreshTokenEventRecorderTest {

	/**
	 * refresh 계열 사건이 사실 그대로 받아 적히는지, 같은 eventId 를 두 번 받아도 한 줄인지, sid 가 없는 사건도
	 *      기록되는지를 확인한다. 같은 컨텍스트에서 이 기록기가 쓰는 저장소의 정렬 질의도 확인한다.
	 *
	 * 주의. 선조회·유니크 위반 흡수·다시 던짐 갈래는 IdempotentInsert 의 알고리즘이고, 그 갈래들은 AuditRecorderBranchTest 가
	 *      AuditRecorder 를 통해 고정한다. 여기서는 이 기록기가 그 알고리즘에 eventId 와 이 테이블의 저장소를 제대로
	 *      넘기는지만 본다.
	 *
	 * 주의. datasource url 을 이 클래스 전용으로 준다. 이름 있는 h2 인메모리 DB 는 여러 @SpringBootTest
	 *      컨텍스트가 공유한다(AuditRecorderTest 와 같은 이유).
	 *
	 * 주의. spring.kafka.listener.auto-startup=false 는 AuditRecorderTest 와 같은 이유다. 로컬에 실제 브로커가
	 *      떠 있으면 리스너가 localhost:9092 로 붙어 실제 사건을 이 테스트의 h2 에 기록해 행 수 단언을 깰 수 있다.
	 */

	private static final String TOPIC = "oidc.refresh-token.lifecycle.v1";

	@Autowired
	private RefreshTokenEventRecorder recorder;

	@Autowired
	private RefreshTokenEventEntityRepository repository;

	@BeforeEach
	void clean() {
		repository.deleteAll();
	}

	@Test
	@DisplayName("REVOKED 사건의 사실을 컬럼마다 그대로 받아 적는다")
	void recordsFacts() {
		Instant before = Instant.now();
		Instant occurredAt = Instant.parse("2026-10-28T01:02:03.456789Z");
		String payload = "{\"eventId\":\"E-1\",\"kind\":\"REVOKED\",\"occurredAt\":\"2026-10-28T01:02:03.456789Z\","
				+ "\"familyId\":\"FAM-1\",\"sub\":\"user-sub-0001\",\"clientId\":\"demo-rp\",\"sid\":\"SID-A\","
				+ "\"reason\":\"REUSE_DETECTED\",\"revokedTokens\":3,\"activeTokensRevoked\":1}";

		boolean recorded = recorder.record(new RefreshTokenEventRecord("E-1", TOPIC, "REVOKED", occurredAt,
				"FAM-1", "user-sub-0001", "demo-rp", "SID-A", null, null, "REUSE_DETECTED", 3, 1, payload));

		assertThat(recorded).isTrue();
		List<RefreshTokenEventEntity> rows = repository.findAll();
		assertThat(rows).hasSize(1);
		RefreshTokenEventEntity row = rows.get(0);
		assertThat(row.getEventId()).isEqualTo("E-1");
		assertThat(row.getEventType()).isEqualTo(TOPIC);
		assertThat(row.getKind()).isEqualTo("REVOKED");
		assertThat(row.getOccurredAt()).isEqualTo(occurredAt);
		assertThat(row.getSub()).isEqualTo("user-sub-0001");
		assertThat(row.getClientId()).isEqualTo("demo-rp");
		assertThat(row.getFamilyId()).isEqualTo("FAM-1");
		assertThat(row.getSid()).isEqualTo("SID-A");
		assertThat(row.getReason()).isEqualTo("REUSE_DETECTED");
		assertThat(row.getRevokedTokens()).isEqualTo(3);
		assertThat(row.getActiveTokensRevoked()).isEqualTo(1);
		assertThat(row.getExpiresAt()).isNull();
		assertThat(row.getFamilyExpiresAt()).isNull();
		assertThat(row.getPayload()).isEqualTo(payload);
		assertThat(row.getRecordedAt()).isAfterOrEqualTo(before.truncatedTo(ChronoUnit.MILLIS));
	}

	@Test
	@DisplayName("같은 eventId 를 두 번 받으면 한 줄이다")
	void sameEventIdIsRecordedOnce() {
		RefreshTokenEventRecord record = record("E-2", "SID-A");

		assertThat(recorder.record(record)).isTrue();
		assertThat(recorder.record(record)).isFalse();

		assertThat(repository.findAll()).hasSize(1);
	}

	@Test
	@DisplayName("sid 가 없는 사건도 기록된다 — sid 는 NULL 이다")
	void nullSidIsRecorded() {
		boolean recorded = recorder.record(record("E-3", null));

		assertThat(recorded).isTrue();
		assertThat(repository.findAll().get(0).getSid()).isNull();
	}

	/**
	 * 한 sub 의 사건을 발생 시각 오름차순으로, 같은 시각이면 기록된 순서(id)로 돌려준다.
	 *
	 * 주의. 시각이 먼저이고 id 는 시각이 같을 때의 결정적 순서다. 같은 sub 의 사건도 계열이 다르면 다른 파티션에서
	 *      올 수 있어 기록된 순서(id)가 발생 순서와 다를 수 있다.
	 */
	@Test
	@DisplayName("한 sub 의 사건을 occurredAt 오름차순, 같으면 id 오름차순으로 돌려준다 — 다른 sub 는 섞이지 않는다")
	void findBySubOrdersByOccurredAtThenId() {
		Instant t1 = Instant.parse("2026-10-28T01:00:00Z");
		Instant t2 = Instant.parse("2026-10-28T02:00:00Z");
		save("E-A", "user-1", t2);
		save("E-B", "user-1", t1);
		save("E-C", "user-1", t2);
		save("E-D", "user-2", t1);

		List<RefreshTokenEventEntity> rows = repository.findBySubOrderByOccurredAtAscIdAsc("user-1");

		assertThat(rows).extracting(RefreshTokenEventEntity::getEventId).containsExactly("E-B", "E-A", "E-C");
		assertThat(repository.existsByEventId("E-D")).isTrue();
		assertThat(repository.existsByEventId("E-Z")).isFalse();
	}

	private static RefreshTokenEventRecord record(String eventId, String sid) {
		return new RefreshTokenEventRecord(eventId, TOPIC, "ISSUED", Instant.parse("2026-10-28T01:02:03.456789Z"),
				"FAM-1", "user-sub-0001", "demo-rp", sid, null, null, null, null, null, "{}");
	}

	private void save(String eventId, String sub, Instant occurredAt) {
		repository.save(RefreshTokenEventEntity.builder()
				.eventId(eventId)
				.eventType(TOPIC)
				.kind("ISSUED")
				.occurredAt(occurredAt)
				.recordedAt(Instant.now())
				.sub(sub)
				.clientId("demo-rp")
				.familyId("FAM-" + eventId)
				.payload("{}")
				.build());
	}
}
