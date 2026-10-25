package dev.starryeye.audit.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
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
	 * 감사 소비자의 토픽 선언과 실패 처리.
	 *
	 * 주의. DLT 이름에 컨슈머 그룹(audit)을 넣는다. 소비자가 하나일 때는 "<토픽>.dlt" 가 모호하지 않았지만,
	 *      같은 토픽을 두 그룹이 소비하면 한 DLT 에 두 소비자의 실패가 섞여 "이 편지는 누가 처리 못 했나"를
	 *      알 수 없게 된다 — 재처리할 때 폐기를 다시 돌려야 할지 감사를 다시 돌려야 할지 모르는데, 한쪽은
	 *      멀쩡히 처리했다. 그래서 token-state 도 같은 슬라이스에서 "...v1.token-state.dlt" 로 이름을 바꾼다.
	 *
	 * 주의. DLT 목적지를 원래 토픽 이름에서 만든다(dltFor(record.topic())). 지금 구독하는 토픽은 하나지만,
	 *      후속 슬라이스에서 토픽이 늘면 각 토픽의 실패가 자기 이름의 DLT 로 간다. 상수 하나로 두면 새 토픽의
	 *      실패가 로그아웃 이름의 DLT 에 섞인다. 다만 브로커의 토픽 자동 생성이 꺼져 있으므로
	 *      (docker-compose.yml 의 KAFKA_AUTO_CREATE_TOPICS_ENABLE=false) 토픽을 더할 때는 그 DLT 의 NewTopic 도
	 *      반드시 함께 선언해야 한다. 없으면 DLT 발행이 실패한다.
	 *
	 * 주의. 본 토픽도 선언한다. 슬라이스 7에서 컨슈머가 토픽 생성 전에 구독하면 파티션 0에만 배정되는
	 *      기동 순서 함정을 겪어 session·token-state 가 둘 다 선언하게 됐고, 감사가 세 번째 선언자다.
	 *      KafkaAdmin 은 파티션을 늘리기만 하므로 선언이 어긋나도 줄어들지는 않지만, 세 곳(session 의
	 *      KafkaTopicConfig, token-state·audit 의 KafkaConsumerConfig)의 파티션 수·replicas 를 함께 맞춰야 한다.
	 *      이 선언의 효력은 기동 시점에 브로커가 응답 가능하다는 전제 위에서만 성립한다 — KafkaAdmin 은
	 *      브로커 미가용이면 로그만 남기고 재시도하지 않는다(token-state 의 KafkaConsumerConfig 주석 참고).
	 *
	 * 주의. 재시도 2회/200ms 후 DLT. 감사 DLT 에 간 편지는 아무도 자동으로 재처리하지 않는다 — 그 이벤트는
	 *      감사에서 빠진 채 남는다.
	 */

	public static String dltFor(String sourceTopic) {
		return sourceTopic + ".audit.dlt";
	}

	@Bean
	NewTopic sessionLoggedOutTopic() {
		return TopicBuilder.name(AuditEventConsumer.LOGGED_OUT_TOPIC).partitions(3).replicas(1).build();
	}

	@Bean
	NewTopic sessionLoggedOutAuditDlt() {
		return TopicBuilder.name(dltFor(AuditEventConsumer.LOGGED_OUT_TOPIC)).partitions(3).replicas(1).build();
	}

	@Bean
	DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
			@Value("${my.consumer-retry-attempts}") long retryAttempts,
			@Value("${my.consumer-retry-interval-ms}") long retryIntervalMs) {
		DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
				(record, exception) -> new TopicPartition(dltFor(record.topic()), -1));
		return new DefaultErrorHandler(recoverer, new FixedBackOff(retryIntervalMs, retryAttempts));
	}
}
