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
	 * 주의. head-of-line blocking(파티션의 뒷 이벤트가 전부 막히는 것)은 이 빈이 처음 막아주는 문제가
	 *      아니다. 커스텀 에러 핸들러 빈이 하나도 없어도 Spring Boot 오토컨피그는 기본
	 *      DefaultErrorHandler(FixedBackOff(0, 9) — 9회, 0ms 간격)를 쓰고, 재시도가 소진되면
	 *      ERROR 로그 한 줄만 남기고 그 레코드를 넘겨 다음 레코드로 진행한다 — 즉 기본값도 이미 유한
	 *      백오프라 뒷줄이 막히지 않는다. `DeadLetterTopicTest` 에서 이 빈을 주석 처리한 채 DLT 단언만
	 *      빼고 돌려보면 "뒤 편지 처리" 단언은 여전히 통과한다(직접 통제 실험으로 확인, task-8-report.md
	 *      참고). 만약 이 빈에 끝없이 재시도하는 백오프를 준다면 그때는 정말로 뒷줄이 막힌다 — 다만
	 *      그건 기본값 얘기가 아니라 일부러 그렇게 설정했을 때의 이야기다.
	 *
	 * 주의. 이 빈이 실제로 바꾸는 것은 두 가지뿐이다. (1) 재시도 횟수·간격을 기본값(9회/0ms)에서
	 *      my.consumer-retry-attempts / my.consumer-retry-interval-ms(2회/200ms)로 명시 제어한다.
	 *      (2) 재시도가 소진된 레코드의 행선지를 "로그 한 줄"에서 조회·재처리 가능한 DLT 토픽
	 *      (oidc.session.logged-out.v1.dlt)으로 바꾼다 — 이 관측성이 이 빈의 실질적 가치다.
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
