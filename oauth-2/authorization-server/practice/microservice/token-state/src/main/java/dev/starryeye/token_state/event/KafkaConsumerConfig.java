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

	/**
	 * 본 토픽(oidc.session.logged-out.v1)을 session 뿐 아니라 token-state 도 선언한다.
	 *
	 * 주의. 이 선언이 없던 상태에서 콜드 스타트 순서가 token-state → session 이면 실제 장애가 재현됐다
	 *      (task-9-report.md 참고). apache/kafka 이미지의 broker 기본값 num.partitions=1 과 kafka-clients
	 *      의 allow.auto.create.topics=true 조합 때문에, token-state 의 consumer 가 session 보다 먼저
	 *      이 토픽을 구독하면 브로커가 그 순간 파티션 1개로 자동 생성해버린다. session 이 나중에
	 *      KafkaAdmin 으로 파티션을 3개로 늘려도(NewTopic 은 이미 있는 토픽을 만나면 파티션이 모자랄 때만
	 *      늘리는 방향으로만 동작한다), token-state 의 consumer group 은 그 순간의 배정(파티션 0 하나)에
	 *      머물러 있다가 다음 메타데이터 갱신(kafka-clients 기본 metadata.max.age.ms=300000, 최대 5분)
	 *      에야 새 파티션을 알아챈다. 그 사이 partition_key(=sid)가 파티션 1·2로 해시되는 로그아웃
	 *      이벤트는 소비되지 않는다 — 로그아웃은 200 을 주고 세션도 지워지고 outbox 의 published_at 도
	 *      채워지는데, refresh 만 폐기되지 않는 조용한 실패다.
	 *
	 * 주의. Spring 의 KafkaAdmin 은 SmartInitializingSingleton 이라 두 서비스 모두 자기 리스너 컨테이너가
	 *      start() 하기 전에 이 빈을 먼저 실행한다. 같은 이름의 토픽을 두 서비스가 각자 선언해도 충돌하지
	 *      않는다 — KafkaAdmin 은 대상 토픽이 이미 있으면 파티션 수를 비교해 부족한 쪽만 늘리고, 이미
	 *      충분하면 아무 일도 하지 않는다(멱등). 그래서 어느 서비스가 먼저 떠도, session 이 아직 하나도
	 *      뜨지 않은 순간에 token-state 만 먼저 떠도 파티션 3개가 보장된다 — "session 을 먼저 올려라"라는
	 *      기동 순서 규율이 아니라 코드가 이 창을 닫는다. 파티션 수·replicas 는 session 의
	 *      KafkaTopicConfig 선언과 반드시 같게 유지해야 한다(어긋나면 늘어나는 쪽으로만 수렴해 혼란스러워진다).
	 *
	 * 주의. 위 "보장된다"는 **그 서비스가 기동하는 시점에 브로커가 이미 응답 가능하다는 전제 위에서만**
	 *      성립한다. spring-kafka KafkaAdmin.initialize() 는 fatalIfBrokerNotAvailable 기본값이 false 라,
	 *      브로커가 미가용이면 에러 로그만 남기고 그 자리에서 조용히 포기한다 — initialize() 를 나중에
	 *      다시 불러 주는 재시도 메커니즘이 없다. 즉 `docker compose up -d` 직후처럼 브로커가 아직 뜨기
	 *      전에 두 서비스가 함께 기동하면, 양쪽 KafkaAdmin 이 모두 이 토픽 생성에 실패한 채로 넘어가고
	 *      그 뒤로는 재기동 전까지 스스로 복구되지 않는다. 그 상태에서 브로커가 뒤늦게 살아나면, 토픽이
	 *      아직 없으므로 처음 구독을 시작하는 consumer 가 브로커의 자동 생성 경로를 탄다(docker-compose.yml
	 *      의 KAFKA_AUTO_CREATE_TOPICS_ENABLE=false 로 그 경로 자체는 막아 뒀지만, 그 설정이 없거나 다른
	 *      환경으로 옮기면 1파티션으로 만들어져 이 문단이 다시 문제가 된다). 이 파일의 "파티션 3개가
	 *      보장된다"는 문장은 그래서 "코드가 기동 순서 규율을 대신한다"까지만 참이고, "브로커 미가용까지
	 *      대신 처리한다"는 뜻은 아니다.
	 */
	@Bean
	NewTopic sessionLoggedOutTopic() {
		return TopicBuilder.name(SessionLoggedOutConsumer.LOGGED_OUT_TOPIC).partitions(3).replicas(1).build();
	}

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
