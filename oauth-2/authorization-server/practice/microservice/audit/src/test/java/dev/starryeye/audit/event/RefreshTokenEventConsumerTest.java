package dev.starryeye.audit.event;

import dev.starryeye.audit.jpa.RefreshTokenEventEntity;
import dev.starryeye.audit.jpa.RefreshTokenEventEntityRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
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
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
		"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
		"spring.datasource.url=jdbc:h2:mem:audit-refresh-consumer;DB_CLOSE_DELAY=-1;MODE=MySQL"
})
@EmbeddedKafka(partitions = 3, topics = {
		RefreshTokenEventConsumer.LIFECYCLE_TOPIC,
		RefreshTokenEventConsumer.LIFECYCLE_TOPIC + ".audit.dlt"
})
class RefreshTokenEventConsumerTest {

	/**
	 * refresh 계열 사건이 refresh_token_events 에 받아 적히는지, 중복 전달이 한 줄로 흡수되는지, 기형 편지가 감사
	 *      DLT 로 가는지를 실제 브로커(임베디드)로 확인한다.
	 *
	 * 주의. 페이로드 문자열은 발행자(token-state 의 RefreshTokenLifecycleEvent)의 JSON 모양을 따른다. 채우지 않은
	 *      선택 필드는 JSON null 이 아니라 키가 빠진다(sid 가 없으면 "sid" 키가 없다). 시각은 ISO-8601 문자열이고
	 *      개수는 정수다.
	 *
	 * 주의. @EmbeddedKafka 의 파티션 수(3)를 KafkaConsumerConfig 의 NewTopic 선언과 같게 둔다. 둘이 어긋나면
	 *      KafkaAdmin 이 토픽을 늘려 테스트 전제가 조용히 깨진다.
	 *
	 * 주의. 이 테스트는 KafkaConsumerConfig 의 NewTopic 선언이 있는지를 증명하지 못한다. 본 토픽과 DLT 를
	 *      @EmbeddedKafka 가 미리 만들고, DLT 프로브(consumeFromAnEmbeddedTopic)는 @EmbeddedKafka 의 topics 목록에 있는
	 *      토픽만 받으므로 DLT 를 그 목록에서 뺄 수도 없다. DLT 의 NewTopic 선언을 지워도 이 테스트는 통과한다. 선언이
	 *      실제 브로커에 토픽을 만드는지는 브로커에서 토픽 목록을 보아야 확인된다.
	 *
	 * 주의. 순서가 필요한 단언은 모든 편지를 같은 키(familyId)로 보낸다. 같은 키는 같은 파티션에 떨어지고, 한
	 *      파티션 안에서는 순서대로 처리되므로 "나중에 보낸 표지 편지가 기록됐다"가 "앞의 편지들도 처리가
	 *      끝났다"를 뜻하게 된다.
	 *
	 * 주의. DLT 소비자는 consumeFromAnEmbeddedTopic 의 3인자(seekToEnd=true) 오버로드로 구독한다. 2인자는
	 *      항상 맨 앞으로 가서 같은 클래스의 앞 테스트가 남긴 레코드를 다시 읽는다.
	 */

	private static final String TOPIC = RefreshTokenEventConsumer.LIFECYCLE_TOPIC;
	private static final String DLT = TOPIC + ".audit.dlt";

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private RefreshTokenEventEntityRepository repository;

	@Autowired
	private EmbeddedKafkaBroker broker;

	private Consumer<String, String> dltConsumer;

	@BeforeEach
	void setUp() {
		repository.deleteAll();
		Map<String, Object> props = KafkaTestUtils.consumerProps("audit-refresh-dlt-probe-" + UUID.randomUUID(), "true", broker);
		dltConsumer = new KafkaConsumer<>(props, new StringDeserializer(), new StringDeserializer());
		broker.consumeFromAnEmbeddedTopic(dltConsumer, true, DLT);
	}

	@AfterEach
	void tearDown() {
		dltConsumer.close();
	}

	@Test
	@DisplayName("ISSUED 사건이 받아 적힌다 — event_type 은 수신 토픽 이름이고 payload 는 원문 그대로다")
	void issuedEventIsRecorded() throws Exception {
		String eventId = UUID.randomUUID().toString();
		// "kind": 뒤의 공백 하나는 원문 그대로 저장하는지와 읽은 JSON 을 다시 직렬화해 저장하는지를 가른다.
		String payload = "{\"eventId\":\"" + eventId + "\",\"kind\": \"ISSUED\",\"occurredAt\":\"2026-10-28T01:02:03.456789Z\","
				+ "\"familyId\":\"FAM-A\",\"sub\":\"user-sub-0001\",\"clientId\":\"demo-rp\",\"sid\":\"SID-A\","
				+ "\"expiresAt\":\"2026-10-28T02:02:03.456789Z\",\"familyExpiresAt\":\"2026-11-27T01:02:03.456789Z\"}";

		send("FAM-A", payload);

		RefreshTokenEventEntity row = awaitRow(eventId);
		assertThat(row.getEventType()).isEqualTo(TOPIC);
		assertThat(row.getKind()).isEqualTo("ISSUED");
		assertThat(row.getOccurredAt()).isEqualTo(Instant.parse("2026-10-28T01:02:03.456789Z"));
		assertThat(row.getFamilyId()).isEqualTo("FAM-A");
		assertThat(row.getSub()).isEqualTo("user-sub-0001");
		assertThat(row.getClientId()).isEqualTo("demo-rp");
		assertThat(row.getSid()).isEqualTo("SID-A");
		assertThat(row.getExpiresAt()).isEqualTo(Instant.parse("2026-10-28T02:02:03.456789Z"));
		assertThat(row.getFamilyExpiresAt()).isEqualTo(Instant.parse("2026-11-27T01:02:03.456789Z"));
		assertThat(row.getReason()).isNull();
		assertThat(row.getRevokedTokens()).isNull();
		assertThat(row.getActiveTokensRevoked()).isNull();
		assertThat(row.getRecordedAt()).isNotNull();
		assertThat(row.getPayload()).isEqualTo(payload);
	}

	@Test
	@DisplayName("REVOKED 사건은 사유와 두 개수를 함께 받아 적는다")
	void revokedEventIsRecordedWithCounts() throws Exception {
		String eventId = UUID.randomUUID().toString();
		String payload = "{\"eventId\":\"" + eventId + "\",\"kind\":\"REVOKED\",\"occurredAt\":\"2026-10-28T03:00:00.000001Z\","
				+ "\"familyId\":\"FAM-B\",\"sub\":\"user-sub-0001\",\"clientId\":\"demo-rp\",\"sid\":\"SID-A\","
				+ "\"reason\":\"SESSION_LOGGED_OUT\",\"revokedTokens\":3,\"activeTokensRevoked\":1}";

		send("FAM-B", payload);

		RefreshTokenEventEntity row = awaitRow(eventId);
		assertThat(row.getKind()).isEqualTo("REVOKED");
		assertThat(row.getReason()).isEqualTo("SESSION_LOGGED_OUT");
		assertThat(row.getRevokedTokens()).isEqualTo(3);
		assertThat(row.getActiveTokensRevoked()).isEqualTo(1);
		assertThat(row.getExpiresAt()).isNull();
		assertThat(row.getFamilyExpiresAt()).isNull();
	}

	@Test
	@DisplayName("sid 키가 없는 사건도 기록된다 — sid 는 NULL 이다")
	void absentSidIsRecordedAsNull() throws Exception {
		String eventId = UUID.randomUUID().toString();
		String payload = "{\"eventId\":\"" + eventId + "\",\"kind\":\"ISSUED\",\"occurredAt\":\"2026-10-28T01:02:03.456789Z\","
				+ "\"familyId\":\"FAM-C\",\"sub\":\"user-sub-0001\",\"clientId\":\"demo-rp\","
				+ "\"expiresAt\":\"2026-10-28T02:02:03.456789Z\",\"familyExpiresAt\":\"2026-11-27T01:02:03.456789Z\"}";

		send("FAM-C", payload);

		RefreshTokenEventEntity row = awaitRow(eventId);
		assertThat(row.getSid()).isNull();
		assertThat(row.getPayload()).isEqualTo(payload);
	}

	@Test
	@DisplayName("같은 사건이 두 번 전달돼도 한 줄이고, 감사 DLT 로 가지 않는다")
	void duplicateDeliveryIsRecordedOnce() throws Exception {
		String eventId = UUID.randomUUID().toString();
		String payload = rotated(eventId, "FAM-D");
		String markerId = UUID.randomUUID().toString();

		send("FAM-D", payload);
		send("FAM-D", payload);
		send("FAM-D", rotated(markerId, "FAM-D"));   // 같은 키 → 앞의 둘이 처리된 뒤 처리된다

		awaitRow(markerId);
		assertThat(rowsOf(eventId)).hasSize(1);
		// 한 줄이라는 사실만으로는 "두 번째가 흡수됐다"와 "두 번째가 예외로 재시도 끝에 DLT 로 갔다"를 가를 수 없다.
		// 표지가 기록됐으므로 앞의 두 편지는 처리가 끝났고, 프로브는 보내기 전에 끝에 서 있었다.
		assertThat(dltConsumer.poll(Duration.ofSeconds(2)).count()).isZero();
	}

	@Test
	@DisplayName("familyId 가 없는 사건은 기록되지 않고 감사 DLT 로 간다")
	void missingFamilyIdGoesToDlt() throws Exception {
		String eventId = UUID.randomUUID().toString();
		String payload = "{\"eventId\":\"" + eventId + "\",\"kind\":\"ROTATED\",\"occurredAt\":\"2026-10-28T01:02:03.456789Z\","
				+ "\"sub\":\"user-sub-0001\",\"clientId\":\"demo-rp\",\"sid\":\"SID-A\","
				+ "\"expiresAt\":\"2026-10-28T02:02:03.456789Z\"}";

		send("FAM-E", payload);

		ConsumerRecord<String, String> dead = KafkaTestUtils.getSingleRecord(dltConsumer, DLT, Duration.ofSeconds(20));
		assertThat(dead.value()).isEqualTo(payload);
		assertThat(causeOf(dead)).isEqualTo(IllegalArgumentException.class.getName());
		assertThat(rowsOf(eventId)).isEmpty();
	}

	@Test
	@DisplayName("개수가 문자열인 REVOKED 사건은 3 으로 바꿔 적지 않고 감사 DLT 로 간다(Review Focus 3)")
	void wrongTypedCountGoesToDlt() throws Exception {
		String eventId = UUID.randomUUID().toString();
		String payload = "{\"eventId\":\"" + eventId + "\",\"kind\":\"REVOKED\",\"occurredAt\":\"2026-10-28T03:00:00.000001Z\","
				+ "\"familyId\":\"FAM-F\",\"sub\":\"user-sub-0001\",\"clientId\":\"demo-rp\",\"sid\":\"SID-A\","
				+ "\"reason\":\"CLIENT_REVOKED\",\"revokedTokens\":\"3\",\"activeTokensRevoked\":1}";

		send("FAM-F", payload);

		ConsumerRecord<String, String> dead = KafkaTestUtils.getSingleRecord(dltConsumer, DLT, Duration.ofSeconds(20));
		assertThat(dead.value()).isEqualTo(payload);
		assertThat(causeOf(dead)).isEqualTo(IllegalArgumentException.class.getName());
		assertThat(rowsOf(eventId)).isEmpty();
	}

	@Test
	@DisplayName("모르는 kind 도 기록된다 — kind 는 받은 그대로 남는다(Review Focus 5)")
	void unknownKindIsRecorded() throws Exception {
		String eventId = UUID.randomUUID().toString();
		String payload = "{\"eventId\":\"" + eventId + "\",\"kind\":\"SUSPENDED\",\"occurredAt\":\"2026-10-28T01:02:03.456789Z\","
				+ "\"familyId\":\"FAM-G\",\"sub\":\"user-sub-0001\",\"clientId\":\"demo-rp\",\"sid\":\"SID-A\"}";

		send("FAM-G", payload);

		RefreshTokenEventEntity row = awaitRow(eventId);
		assertThat(row.getKind()).isEqualTo("SUSPENDED");
		assertThat(row.getPayload()).isEqualTo(payload);
	}

	/**
	 * 감사 DLT 레코드 헤더에서 읽은, 실패를 일으킨 예외의 클래스 이름. 소비자가 JsonFields 로 먼저 거른 편지는
	 *      IllegalArgumentException 이다. 거르지 못하고 기록기까지 간 편지는 not null 위반(DataIntegrityViolationException)
	 *      으로 같은 DLT 에 가므로, DLT 에 원문이 있고 행이 없다는 단언만으로는 소비자가 걸렀는지 가려지지 않는다.
	 */
	private static String causeOf(ConsumerRecord<String, String> dead) {
		Header header = dead.headers().lastHeader(KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN);
		assertThat(header).as("DLT 레코드의 원인 예외 헤더").isNotNull();
		return new String(header.value(), StandardCharsets.UTF_8);
	}

	private void send(String key, String payload) throws Exception {
		kafkaTemplate.send(TOPIC, key, payload).get(5, TimeUnit.SECONDS);
	}

	private static String rotated(String eventId, String familyId) {
		return "{\"eventId\":\"" + eventId + "\",\"kind\":\"ROTATED\",\"occurredAt\":\"2026-10-28T01:02:03.456789Z\","
				+ "\"familyId\":\"" + familyId + "\",\"sub\":\"user-sub-0001\",\"clientId\":\"demo-rp\",\"sid\":\"SID-A\","
				+ "\"expiresAt\":\"2026-10-28T02:02:03.456789Z\"}";
	}

	private RefreshTokenEventEntity awaitRow(String eventId) {
		await().atMost(20, TimeUnit.SECONDS).until(() -> !rowsOf(eventId).isEmpty());
		return rowsOf(eventId).get(0);
	}

	private List<RefreshTokenEventEntity> rowsOf(String eventId) {
		return repository.findAll().stream().filter(row -> eventId.equals(row.getEventId())).toList();
	}
}
