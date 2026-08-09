package dev.starryeye.session.outbox;

import dev.starryeye.session.SessionService;
import dev.starryeye.session.event.KafkaTopicConfig;
import dev.starryeye.session.jpa.OutboxEntityRepository;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
		"spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
		"my.outbox-poll-interval-ms=3600000" // 자동 실행을 사실상 끄고 직접 호출해 검증한다
})
@EmbeddedKafka(partitions = 3, topics = KafkaTopicConfig.LOGGED_OUT_TOPIC)
class OutboxPublisherTest {

	/**
	 * OutboxPublisher.publishPending() 이 미발행 행을 Kafka 로 옮기고 published_at 을 남기는지 본다.
	 *
	 * 주의. my.outbox-poll-interval-ms 를 1시간으로 잡아 @Scheduled 자동 실행을 사실상 꺼둔다.
	 *      @EnableScheduling 이 테스트 컨텍스트에도 적용되므로, 자동 실행과 아래의 직접 호출이 겹치면
	 *      같은 행을 두 번 세거나 타이밍에 따라 결과가 흔들릴 수 있다.
	 */

	@Autowired
	private SessionService sessionService;

	@Autowired
	private OutboxPublisher publisher;

	@Autowired
	private OutboxEntityRepository outboxRepository;

	@Autowired
	private EmbeddedKafkaBroker broker;

	private Consumer<String, String> consumer;

	@BeforeEach
	void subscribe() {
		Map<String, Object> props = KafkaTestUtils.consumerProps("outbox-test", "true", broker);
		consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(
				props, new StringDeserializer(), new StringDeserializer());
		// 주의. seekToEnd=true 로 구독한다. LogoutEventPublishTest 와 같은 이유다 — 기본(2-인자) 오버로드는
		//      항상 파티션 처음부터 읽어, 같은 클래스 안 다른 테스트가 이미 발행해 둔 레코드까지 다시
		//      읽어버린다. publishesPendingAndMarks 가 먼저 돌면 그 레코드가 doesNotResendPublishedRows 의
		//      getSingleRecord 에도 잡혀 "레코드가 둘"로 실패한다.
		broker.consumeFromAnEmbeddedTopic(consumer, true, KafkaTopicConfig.LOGGED_OUT_TOPIC);
	}

	@AfterEach
	void close() {
		consumer.close();
	}

	@Test
	@DisplayName("미발행 행을 Kafka 로 옮기고 발행 표시를 남긴다")
	void publishesPendingAndMarks() {
		sessionService.register("SID-A", "user-sub-0001", "my-client");
		sessionService.consumeForLogout("SID-A");
		assertThat(outboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc()).hasSize(1);

		publisher.publishPending();

		ConsumerRecord<String, String> record =
				KafkaTestUtils.getSingleRecord(consumer, KafkaTopicConfig.LOGGED_OUT_TOPIC);
		assertThat(record.key()).isEqualTo("SID-A");
		assertThat(outboxRepository.findTop100ByPublishedAtIsNullOrderByIdAsc()).isEmpty();
	}

	@Test
	@DisplayName("두 번 돌려도 같은 편지를 다시 보내지 않는다")
	void doesNotResendPublishedRows() {
		sessionService.register("SID-B", "user-sub-0001", "my-client");
		sessionService.consumeForLogout("SID-B");

		publisher.publishPending();
		KafkaTestUtils.getSingleRecord(consumer, KafkaTopicConfig.LOGGED_OUT_TOPIC);

		publisher.publishPending();

		assertThat(KafkaTestUtils.getRecords(consumer, java.time.Duration.ofSeconds(2)).count()).isZero();
	}
}
