package dev.starryeye.token_state.outbox;

import dev.starryeye.token_state.IssueResult;
import dev.starryeye.token_state.OutboxEvents;
import dev.starryeye.token_state.RefreshTokenService;
import dev.starryeye.token_state.event.RefreshTokenEventPublisher;
import dev.starryeye.token_state.jpa.OutboxEntity;
import dev.starryeye.token_state.jpa.OutboxEntityRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
		// 주의. 전용 h2 를 쓴다. 이 모듈의 기본 h2(jdbc:h2:mem:token-state)는 이름 있는(named) 인메모리 DB 라
		//      프로퍼티가 달라 컨텍스트 캐시가 갈린 다른 @SpringBootTest 도 프로세스 안에서는 같은 outbox
		//      테이블을 공유한다. 그쪽이 남긴 미발행 행을 이 클래스의 publishPending() 이 함께 집어 보내면
		//      단건 수신 단언이 "레코드가 둘"로 깨질 수 있다. 이 클래스만의 이름을 줘 물리적으로 분리한다.
		"spring.datasource.url=jdbc:h2:mem:token-state-outbox-publisher;DB_CLOSE_DELAY=-1;MODE=MySQL"
})
@EmbeddedKafka(partitions = 3, topics = RefreshTokenEventPublisher.LIFECYCLE_TOPIC)
class OutboxPublisherTest {

	/**
	 * OutboxPublisher.publishPending() 이 미발행 행을 계열(familyId)을 키로 Kafka 에 보내고 published_at 을
	 *      남기는지 본다. 보내는 곳은 EmbeddedKafka 뿐이다.
	 *
	 * 주의. my.outbox-poll-interval-ms 는 테스트 yml 의 3600000(1시간)이라 @Scheduled 자동 실행은 사실상
	 *      꺼져 있다. @EnableScheduling 이 테스트 컨텍스트에도 적용되므로, 자동 실행이 켜져 있으면 아래의
	 *      직접 호출과 겹쳐 같은 행을 두 번 세거나 타이밍에 따라 결과가 흔들릴 수 있다.
	 */

	@Autowired
	private RefreshTokenService service;

	@Autowired
	private OutboxPublisher publisher;

	@Autowired
	private OutboxEntityRepository outboxRepository;

	@Autowired
	private EmbeddedKafkaBroker broker;

	private Consumer<String, String> consumer;

	@BeforeEach
	void subscribe() {
		Map<String, Object> props = KafkaTestUtils.consumerProps("lifecycle-test", "true", broker);
		consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(
				props, new StringDeserializer(), new StringDeserializer());
		// 주의. seekToEnd=true 로 구독한다. 기본(2-인자) 오버로드는 항상 파티션 처음부터 읽어, 같은 클래스 안
		//      다른 테스트가 이미 발행해 둔 레코드까지 다시 읽는다. 먼저 돈 테스트의 레코드가 나중 테스트의
		//      getSingleRecord 에도 잡혀 "레코드가 둘"로 실패한다.
		broker.consumeFromAnEmbeddedTopic(consumer, true, RefreshTokenEventPublisher.LIFECYCLE_TOPIC);
	}

	@AfterEach
	void close() {
		consumer.close();
	}

	@Test
	@DisplayName("미발행 행을 계열을 키로 보내고 발행 표시를 남긴다")
	void publishesPendingRowsKeyedByFamilyAndMarksThem() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

		publisher.publishPending();

		ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
				consumer, RefreshTokenEventPublisher.LIFECYCLE_TOPIC, Duration.ofSeconds(10));
		OutboxEntity row = OutboxEvents.rowsOf(outboxRepository, issued.familyId()).get(0);
		assertThat(record.key()).isEqualTo(issued.familyId());
		assertThat(record.value()).isEqualTo(row.getPayload());
		assertThat(row.getPublishedAt()).isNotNull();
	}

	@Test
	@DisplayName("이미 보낸 행은 다시 보내지 않는다")
	void doesNotResendPublishedRows() {
		service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-B");
		publisher.publishPending();
		KafkaTestUtils.getSingleRecord(consumer, RefreshTokenEventPublisher.LIFECYCLE_TOPIC, Duration.ofSeconds(10));

		publisher.publishPending();

		assertThat(consumer.poll(Duration.ofSeconds(2)).count()).isZero();
	}
}
