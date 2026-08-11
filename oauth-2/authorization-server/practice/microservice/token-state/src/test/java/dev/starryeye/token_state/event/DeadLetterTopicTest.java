package dev.starryeye.token_state.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.token_state.IssueResult;
import dev.starryeye.token_state.RefreshTokenService;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
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
	 * 주의. @EmbeddedKafka(partitions = 1) 을 줘도 실제 파티션 수를 1로 믿으면 안 된다.
	 *      KafkaConsumerConfig 의 NewTopic 빈들이 partitions(3) 을 선언하고 있고, Spring 의 KafkaAdmin 은
	 *      SmartInitializingSingleton 이라 EmbeddedKafkaKraftBroker(InitializingBean, 토픽을 1개로 먼저
	 *      만든다)보다 반드시 뒤에 실행돼 그 토픽을 3으로 늘려 버린다(checkPartitions, "모자라면 늘리기만
	 *      한다"). 그래서 두 편지를 서로 다른 키로 보내면 kafka-clients 의 기본 파티셔너가 둘을 다른
	 *      파티션에 흩어버릴 수 있다 — 실제로 "SID-POISON"/"SID-GOOD" 키를 썼을 때 murmur2 해시로
	 *      서로 다른 파티션에 떨어졌고, 그 상태에서는 "뒤가 막히지 않는다" 단언이 애초에 갈라져 있던
	 *      큐를 우연히 통과한 것일 뿐 아무것도 증명하지 못했다. 그래서 두 편지를 **같은 키**로 보낸다 —
	 *      같은 키는 파티션 수와 무관하게 항상 같은 파티션에 떨어지므로(파티셔너는 key 의 해시값만 보고
	 *      partition 수로 나눈 나머지를 쓴다) 이 전제가 실제 파티션 개수에 좌우되지 않는다. 그리고
	 *      두 RecordMetadata.partition() 이 실제로 같은지 테스트가 스스로 단언해 그 전제를 재확인한다
	 *      (아래 poisonMetadata/goodMetadata 비교 단언) — 이 단언이 실패하면 이 테스트 자체가 무의미하다는 뜻이다.
	 *
	 * 주의. "뒤가 막히지 않는다" 단언이 통과하는 것을 KafkaConsumerConfig.kafkaErrorHandler 빈의 성과로
	 *      읽지 마라 — 그 빈을 주석 처리하고 아래 DLT 단언만 뺀 채 돌려도 이 단언은 여전히 통과한다
	 *      (Spring Boot 기본 에러 핸들러도 유한 재시도 뒤 로그만 남기고 다음 레코드로 넘어가기 때문,
	 *      KafkaConsumerConfig 주석 참고). 이 클래스가 kafkaErrorHandler 빈에 대해 실제로 검증하는
	 *      것은 "DLT 로 간다"는 부분이다 — DLT 토픽은 그 빈 없이는 아무것도 받지 않는다.
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

		// 두 편지 모두 같은 키로 보낸다 — 파티션 수(현재 3, 클래스 주석 참고)와 무관하게 같은 파티션에
		// 떨어지게 하려는 것이다. 키 자체는 페이로드 내용과 무관하다(첫 편지는 애초에 JSON 도 아니다).
		String partitionKey = "SID-GOOD";

		// 1) 역직렬화가 불가능한 편지 — 몇 번을 시도해도 낫지 않는다
		SendResult<String, String> poisonResult = kafkaTemplate
				.send(SessionLoggedOutConsumer.LOGGED_OUT_TOPIC, partitionKey, "{not json").get(5, TimeUnit.SECONDS);

		// 2) 뒤이어 정상 편지
		String good = objectMapper.writeValueAsString(new SessionLoggedOutEvent(
				UUID.randomUUID().toString(), "SID-GOOD", "user-sub-0001", Instant.now()));
		SendResult<String, String> goodResult = kafkaTemplate
				.send(SessionLoggedOutConsumer.LOGGED_OUT_TOPIC, partitionKey, good).get(5, TimeUnit.SECONDS);

		// 이 테스트의 전제 자체를 확인한다 — 두 편지가 실제로 같은 파티션에 있어야 "뒤가 막히지 않는다"가
		// 의미를 갖는다. 다르면 이 단언이 실패해 테스트가 무의미했음을 바로 드러낸다.
		RecordMetadata poisonMetadata = poisonResult.getRecordMetadata();
		RecordMetadata goodMetadata = goodResult.getRecordMetadata();
		assertThat(poisonMetadata.partition())
				.as("poison(partition=%d) 과 good(partition=%d) 편지가 같은 파티션에 있어야 head-of-line blocking 여부를 확인할 수 있다",
						poisonMetadata.partition(), goodMetadata.partition())
				.isEqualTo(goodMetadata.partition());

		// 앞 편지가 DLT 로 빠진다
		assertThat(KafkaTestUtils.getSingleRecord(dltConsumer, KafkaConsumerConfig.LOGGED_OUT_DLT,
				java.time.Duration.ofSeconds(20)).value()).isEqualTo("{not json");

		// 뒤 편지는 막히지 않고 처리된다
		await().atMost(20, TimeUnit.SECONDS).untilAsserted(() ->
				assertThat(repository.findByFamilyId(issued.familyId()).get(0).getStatus())
						.isEqualTo(RefreshTokenStatus.REVOKED));
	}
}
