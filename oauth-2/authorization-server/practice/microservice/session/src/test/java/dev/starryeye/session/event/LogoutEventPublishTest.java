package dev.starryeye.session.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.session.SessionService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
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

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 3, topics = KafkaTopicConfig.LOGGED_OUT_TOPIC)
class LogoutEventPublishTest {

	/**
	 * 로그아웃하면 그 사실이 토픽에 실린다. 파티션 키는 sid 여야 한다 — 같은 세션의 이벤트가 같은
	 *      파티션에 들어가야 순서가 보장되기 때문이다.
	 */

	@Autowired
	private SessionService sessionService;

	@Autowired
	private EmbeddedKafkaBroker broker;

	@Autowired
	private ObjectMapper objectMapper;

	private Consumer<String, String> consumer;

	@BeforeEach
	void subscribe() {
		Map<String, Object> props = KafkaTestUtils.consumerProps("test-group", "true", broker);
		consumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(props,
				new org.apache.kafka.common.serialization.StringDeserializer(),
				new org.apache.kafka.common.serialization.StringDeserializer());
		// 주의. seekToEnd=true 로 구독한다. 기본(2-인자) 오버로드는 항상 파티션 처음부터 읽어,
		//      같은 클래스 안 다른 테스트가 이미 발행해 둔 레코드까지 다시 읽어버린다 — 두 테스트가
		//      같은 임베디드 브로커·토픽을 공유하므로 나중에 도는 쪽이 "레코드가 둘"로 실패한다.
		broker.consumeFromAnEmbeddedTopic(consumer, true, KafkaTopicConfig.LOGGED_OUT_TOPIC);
	}

	@AfterEach
	void close() {
		consumer.close();
	}

	@Test
	@DisplayName("로그아웃하면 sid 를 키로 이벤트가 발행된다")
	void publishesWithSidAsKey() throws Exception {
		sessionService.register("SID-A", "user-sub-0001", "my-client");

		sessionService.consumeForLogout("SID-A");

		ConsumerRecord<String, String> record =
				KafkaTestUtils.getSingleRecord(consumer, KafkaTopicConfig.LOGGED_OUT_TOPIC);
		assertThat(record.key()).isEqualTo("SID-A");

		SessionLoggedOutEvent event = objectMapper.readValue(record.value(), SessionLoggedOutEvent.class);
		assertThat(event.sid()).isEqualTo("SID-A");
		assertThat(event.sub()).isEqualTo("user-sub-0001");
		assertThat(event.eventId()).isNotBlank();
		assertThat(event.occurredAt()).isNotNull();
	}

	@Test
	@DisplayName("등록된 RP 가 없는 세션도 발행한다 — 그 sid 의 refresh token 이 있을 수 있다")
	void publishesEvenWithoutRegisteredRps() throws Exception {
		sessionService.consumeForLogout("SID-NO-RP");

		ConsumerRecord<String, String> record =
				KafkaTestUtils.getSingleRecord(consumer, KafkaTopicConfig.LOGGED_OUT_TOPIC);
		SessionLoggedOutEvent event = objectMapper.readValue(record.value(), SessionLoggedOutEvent.class);
		assertThat(event.sid()).isEqualTo("SID-NO-RP");
		assertThat(event.sub()).isNull();
	}
}
