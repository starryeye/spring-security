package dev.starryeye.audit.event;

import dev.starryeye.audit.jpa.AuditEventEntity;
import dev.starryeye.audit.jpa.AuditEventEntityRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
		"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
		"spring.datasource.url=jdbc:h2:mem:audit-consumer;DB_CLOSE_DELAY=-1;MODE=MySQL"
})
@EmbeddedKafka(partitions = 3, topics = {
		AuditEventConsumer.LOGGED_OUT_TOPIC,
		AuditEventConsumer.LOGGED_OUT_TOPIC + ".audit.dlt"
})
class AuditEventConsumerTest {

	/**
	 * 로그아웃 이벤트가 감사 레코드로 받아 적히는지, 중복 전달이 한 줄로 흡수되는지, 기형 편지가 감사 DLT 로
	 *      가는지를 실제 브로커(임베디드)로 확인한다.
	 *
	 * 주의. @EmbeddedKafka 의 파티션 수(3)를 KafkaConsumerConfig 의 NewTopic 선언과 같게 둔다. 슬라이스 7에서
	 *      둘이 어긋나 KafkaAdmin 이 토픽을 늘리는 바람에 테스트 전제가 조용히 깨진 일이 있었다.
	 *
	 * 주의. 순서가 필요한 단언은 모든 편지를 같은 키로 보낸다. 같은 키는 같은 파티션에 떨어지고, 한 파티션
	 *      안에서는 순서대로 처리되므로 "나중에 보낸 표지 편지가 기록됐다"가 "앞의 편지들도 처리가 끝났다"를
	 *      뜻하게 된다. 기다림을 시간에 맡기지 않고 표지로 확정하는 방법이다.
	 *
	 * 주의. DLT 소비자는 consumeFromAnEmbeddedTopic 의 3인자(seekToEnd=true) 오버로드로 구독한다. 2인자는
	 *      항상 맨 앞으로 가서 같은 클래스의 앞 테스트가 남긴 레코드를 다시 읽는다(슬라이스 7에서 겪었다).
	 */

	private static final String TOPIC = AuditEventConsumer.LOGGED_OUT_TOPIC;
	private static final String DLT = TOPIC + ".audit.dlt";

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private AuditEventEntityRepository repository;

	@Autowired
	private EmbeddedKafkaBroker broker;

	private Consumer<String, String> dltConsumer;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
		Map<String, Object> props = KafkaTestUtils.consumerProps("audit-dlt-probe-" + UUID.randomUUID(), "true", broker);
		dltConsumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer());
		broker.consumeFromAnEmbeddedTopic(dltConsumer, true, DLT);
	}

	@AfterEach
	void tearDown() {
		dltConsumer.close();
	}

	@Test
	@DisplayName("로그아웃 이벤트가 감사 레코드로 받아 적힌다 — event_type 은 수신 토픽 이름 그대로다")
	void logoutEventIsRecorded() throws Exception {
		String eventId = UUID.randomUUID().toString();
		String payload = event(eventId, "SID-A", "\"user-sub-0001\"");

		send("SID-A", payload);

		AuditEventEntity row = awaitRow(eventId);
		assertThat(row.getEventType()).isEqualTo(TOPIC);
		assertThat(row.getSubjectSid()).isEqualTo("SID-A");
		assertThat(row.getReportedSub()).isEqualTo("user-sub-0001");
		assertThat(row.getOccurredAt()).isEqualTo(java.time.Instant.parse("2026-09-28T01:02:03.456Z"));
		assertThat(row.getPayload()).isEqualTo(payload);
	}

	@Test
	@DisplayName("같은 이벤트가 두 번 전달돼도 한 줄이다")
	void duplicateDeliveryIsRecordedOnce() throws Exception {
		String eventId = UUID.randomUUID().toString();
		String payload = event(eventId, "SID-B", "\"user-sub-0001\"");
		String markerId = UUID.randomUUID().toString();

		send("SID-B", payload);
		send("SID-B", payload);
		send("SID-B", event(markerId, "SID-B", "\"user-sub-0001\""));  // 같은 키 → 앞의 둘이 처리된 뒤 처리된다

		awaitRow(markerId);
		assertThat(rowsOf(eventId)).hasSize(1);
	}

	@Test
	@DisplayName("sub 가 없는 이벤트도 기록된다 — reported_sub 는 NULL(Review Focus 1)")
	void nullSubIsRecorded() throws Exception {
		String eventId = UUID.randomUUID().toString();

		send("SID-NO-RP", event(eventId, "SID-NO-RP", "null"));

		assertThat(awaitRow(eventId).getReportedSub()).isNull();
	}

	@Test
	@DisplayName("발행자가 모르는 필드를 더해도 기록되고, 원문에 그 필드가 남는다(Review Focus 2)")
	void unknownFieldIsPreservedInPayload() throws Exception {
		String eventId = UUID.randomUUID().toString();
		String payload = "{\"eventId\":\"" + eventId + "\",\"sid\":\"SID-C\",\"sub\":\"user-sub-0001\","
				+ "\"occurredAt\":\"2026-09-28T01:02:03.456Z\",\"addedLater\":\"v1-compatible\"}";

		send("SID-C", payload);

		assertThat(awaitRow(eventId).getPayload()).contains("\"addedLater\":\"v1-compatible\"");
	}

	@Test
	@DisplayName("sid 가 빠진 기형 이벤트는 기록되지 않고 감사 DLT 로 간다 — 조용히 흡수되지 않는다")
	void missingSidGoesToAuditDlt() throws Exception {
		String eventId = UUID.randomUUID().toString();
		String payload = "{\"eventId\":\"" + eventId + "\",\"sub\":\"user-sub-0001\",\"occurredAt\":\"2026-09-28T01:02:03.456Z\"}";

		send("SID-D", payload);

		assertThat(KafkaTestUtils.getSingleRecord(dltConsumer, DLT, Duration.ofSeconds(20)).value()).isEqualTo(payload);
		assertThat(rowsOf(eventId)).isEmpty();
	}

	@Test
	@DisplayName("JSON 이 아닌 편지는 감사 DLT 로 가고, 뒤 편지는 기록된다(Review Focus 3)")
	void poisonGoesToAuditDlt() throws Exception {
		String goodId = UUID.randomUUID().toString();

		send("SID-E", "{not json");
		send("SID-E", event(goodId, "SID-E", "\"user-sub-0001\""));   // 같은 키 → poison 뒤에 선다

		assertThat(KafkaTestUtils.getSingleRecord(dltConsumer, DLT, Duration.ofSeconds(20)).value()).isEqualTo("{not json");
		awaitRow(goodId);
	}

	private void send(String key, String payload) throws Exception {
		kafkaTemplate.send(TOPIC, key, payload).get(5, TimeUnit.SECONDS);
	}

	private static String event(String eventId, String sid, String subJson) {
		return "{\"eventId\":\"" + eventId + "\",\"sid\":\"" + sid + "\",\"sub\":" + subJson
				+ ",\"occurredAt\":\"2026-09-28T01:02:03.456Z\"}";
	}

	private AuditEventEntity awaitRow(String eventId) {
		await().atMost(20, TimeUnit.SECONDS).until(() -> !rowsOf(eventId).isEmpty());
		return rowsOf(eventId).get(0);
	}

	private List<AuditEventEntity> rowsOf(String eventId) {
		return repository.findAll().stream().filter(row -> eventId.equals(row.getEventId())).toList();
	}
}
