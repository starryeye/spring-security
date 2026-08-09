package dev.starryeye.token_state.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.token_state.IssueResult;
import dev.starryeye.token_state.RefreshTokenService;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.apache.kafka.clients.consumer.Consumer;
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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@EmbeddedKafka(partitions = 1, topics = {
		SessionLoggedOutConsumer.LOGGED_OUT_TOPIC,
		KafkaConsumerConfig.LOGGED_OUT_DLT
})
class DeadLetterTopicTest {

	/**
	 * 처리할 수 없는 편지 하나가 파티션 전체를 막으면 안 된다. 한 사용자의 폐기가 안 되는 동안
	 *      다른 사용자들 폐기까지 멈추기 때문이다(head-of-line blocking).
	 *
	 * 주의. 파티션을 1로 잡는다. 두 편지가 반드시 같은 줄에 서야 "뒤가 막히지 않는다"를 확인할 수 있다.
	 */

	@Autowired
	private RefreshTokenService service;

	@Autowired
	private RefreshTokenEntityRepository repository;

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private EmbeddedKafkaBroker broker;

	private Consumer<String, String> dltConsumer;

	@BeforeEach
	void subscribeDlt() {
		Map<String, Object> props = KafkaTestUtils.consumerProps("dlt-test", "true", broker);
		dltConsumer = new org.apache.kafka.clients.consumer.KafkaConsumer<>(
				props, new StringDeserializer(), new StringDeserializer());
		broker.consumeFromAnEmbeddedTopic(dltConsumer, KafkaConsumerConfig.LOGGED_OUT_DLT);
	}

	@AfterEach
	void close() {
		dltConsumer.close();
	}

	@Test
	@DisplayName("처리할 수 없는 편지는 DLT 로 가고 뒤 편지는 정상 처리된다")
	void poisonMessageGoesToDltAndDoesNotBlockTheQueue() throws Exception {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-GOOD");

		// 1) 역직렬화가 불가능한 편지 — 몇 번을 시도해도 낫지 않는다
		kafkaTemplate.send(SessionLoggedOutConsumer.LOGGED_OUT_TOPIC, "SID-POISON", "{not json").get(5, TimeUnit.SECONDS);

		// 2) 뒤이어 정상 편지
		String good = objectMapper.writeValueAsString(new SessionLoggedOutEvent(
				UUID.randomUUID().toString(), "SID-GOOD", "user-sub-0001", Instant.now()));
		kafkaTemplate.send(SessionLoggedOutConsumer.LOGGED_OUT_TOPIC, "SID-GOOD", good).get(5, TimeUnit.SECONDS);

		// 앞 편지가 DLT 로 빠진다
		assertThat(KafkaTestUtils.getSingleRecord(dltConsumer, KafkaConsumerConfig.LOGGED_OUT_DLT,
				java.time.Duration.ofSeconds(20)).value()).isEqualTo("{not json");

		// 뒤 편지는 막히지 않고 처리된다
		await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
				assertThat(repository.findByFamilyId(issued.familyId()).get(0).getStatus())
						.isEqualTo(RefreshTokenStatus.REVOKED));
	}
}
