package dev.starryeye.token_state.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {

	/**
	 * 소비 실패를 제한 재시도 후 DLT 로 보낸다.
	 *
	 * 주의. 무한 재시도를 걸면 그 파티션의 뒷 이벤트가 전부 막힌다(head-of-line blocking). 한 사용자의
	 *      폐기가 안 되는 동안 다른 사용자들 폐기까지 멈춘다. 그래서 몇 번 시도하고 안 되면 그 편지를
	 *      따로 빼놓고 다음으로 넘어간다 — 실패가 조용히 사라지지도 않고, 줄도 막히지 않는다.
	 *
	 * 주의. DLT 로 보낸 편지는 아무도 자동으로 다시 처리하지 않는다. 그 세션의 refresh token 은 폐기되지
	 *      않은 채 남는다. 로그와 DLT 가 그 사실을 남기는 유일한 수단이므로, 운영이라면 DLT 적재를
	 *      경보 대상으로 삼아야 한다.
	 */
	public static final String LOGGED_OUT_DLT = SessionLoggedOutConsumer.LOGGED_OUT_TOPIC + ".dlt";

	@Bean
	NewTopic sessionLoggedOutDlt() {
		return TopicBuilder.name(LOGGED_OUT_DLT).partitions(3).replicas(1).build();
	}

	@Bean
	DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
			@Value("${my.consumer-retry-attempts}") long retryAttempts,
			@Value("${my.consumer-retry-interval-ms}") long retryIntervalMs) {
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
				(record, exception) -> new org.apache.kafka.common.TopicPartition(LOGGED_OUT_DLT, -1));
		return new DefaultErrorHandler(recoverer, new FixedBackOff(retryIntervalMs, retryAttempts));
	}
}
