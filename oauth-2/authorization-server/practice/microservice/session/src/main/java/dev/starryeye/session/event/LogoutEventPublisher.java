package dev.starryeye.session.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class LogoutEventPublisher {

	/**
	 * 로그아웃 사실을 Kafka 로 발행한다.
	 *
	 * 주의. 파티션 키는 sid 다. 같은 세션의 이벤트가 같은 파티션에 들어가 순서가 보장된다. sub 로 잡으면
	 *      한 사용자의 모든 세션이 한 파티션에 몰리는데, 세션 간에는 순서 제약이 없으므로 병렬성만 잃는다.
	 *
	 * 주의. send 의 결과를 기다린다(블로킹). 기다리지 않으면 발행 실패가 조용히 사라져 "로그아웃했는데
	 *      refresh 는 살아 있다"가 아무 흔적 없이 일어난다. 다만 이 선택은 SessionService 의 트랜잭션
	 *      안에서 호출되므로 Kafka 장애가 로그아웃 트랜잭션 전체를 롤백시킨다 — 슬라이스 7 Task 7 이
	 *      outbox 로 닫는 문제가 바로 이것이다.
	 */

	private final KafkaTemplate<String, String> kafkaTemplate;
	private final ObjectMapper objectMapper;

	public void publish(String sid, String sub) {
		SessionLoggedOutEvent event = new SessionLoggedOutEvent(
				UUID.randomUUID().toString(), sid, sub, Instant.now());
		try {
			String payload = objectMapper.writeValueAsString(event);
			kafkaTemplate.send(KafkaTopicConfig.LOGGED_OUT_TOPIC, sid, payload)
					.get(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("logout event publish interrupted for sid=" + sid, e);
		} catch (Exception e) {
			throw new IllegalStateException("logout event publish failed for sid=" + sid, e);
		}
	}
}
