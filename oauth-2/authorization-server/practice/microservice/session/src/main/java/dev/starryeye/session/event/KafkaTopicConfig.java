package dev.starryeye.session.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

	/**
	 * 자동 생성에 맡기지 않고 파티션 수를 명시한다. 자동 생성은 기본 1 파티션이라 순서 보장의 단위가
	 *      토픽 전체가 되어버린다 — 세션끼리 줄을 설 이유가 없는데 전부 직렬화된다.
	 */
	public static final String LOGGED_OUT_TOPIC = "oidc.session.logged-out.v1";

	@Bean
	NewTopic sessionLoggedOutTopic() {
		return TopicBuilder.name(LOGGED_OUT_TOPIC).partitions(3).replicas(1).build();
	}
}
