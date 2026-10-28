package dev.starryeye.token_state.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

	/**
	 * 자동 생성에 맡기지 않고 파티션 수를 명시한다. 자동 생성은 기본 1 파티션이라 순서 보장의 단위가
	 *      토픽 전체가 되어버린다 — 계열끼리 줄을 설 이유가 없는데 전부 직렬화된다.
	 *
	 * 주의. 이 토픽을 읽는 audit 의 KafkaConsumerConfig 가 같은 토픽을 선언할 때도 파티션 수와 replicas 가 이
	 *      선언과 같아야 한다. 어긋나면 KafkaAdmin 은 파티션을 늘리는 방향으로만 수렴시킨다(KafkaConsumerConfig 의
	 *      sessionLoggedOutTopic 주석 참고).
	 */

	@Bean
	NewTopic refreshTokenLifecycleTopic() {
		return TopicBuilder.name(RefreshTokenEventPublisher.LIFECYCLE_TOPIC).partitions(3).replicas(1).build();
	}
}
