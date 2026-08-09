package dev.starryeye.session.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.session.jpa.OutboxEntity;
import dev.starryeye.session.jpa.OutboxEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class LogoutEventPublisher {

	/**
	 * 로그아웃 사실을 outbox 에 기록한다. Kafka 를 직접 부르지 않는다.
	 *
	 * 주의. 호출자(SessionService.consumeForLogout)의 트랜잭션에 참여한다. oidc_sessions 삭제와 이 INSERT 가
	 *      함께 커밋되거나 함께 롤백되므로, 상태 변경과 이벤트 기록 사이의 틈이 사라진다. Kafka 로 옮기는 일은
	 *      OutboxPublisher 가 별도 주기로 하고, 실패해도 행이 DB 에 남아 다음 주기에 다시 시도된다.
	 *
	 * 주의. 파티션 키는 sid 다. 같은 세션의 이벤트가 같은 파티션에 들어가야 순서가 보장된다.
	 */

	private final OutboxEntityRepository outboxRepository;
	private final ObjectMapper objectMapper;

	public void record(String sid, String sub) {
		SessionLoggedOutEvent event = new SessionLoggedOutEvent(
				UUID.randomUUID().toString(), sid, sub, Instant.now());
		String payload;
		try {
			payload = objectMapper.writeValueAsString(event);
		} catch (JsonProcessingException e) {
			// 직렬화 실패는 재시도로 낫지 않는다. 트랜잭션을 죽여 로그아웃 자체를 실패시킨다 —
			// 기록하지 못한 이벤트를 성공한 것처럼 커밋하면 그 사실이 영원히 사라진다.
			throw new IllegalStateException("failed to serialize logout event for sid=" + sid, e);
		}

		outboxRepository.save(OutboxEntity.builder()
				.eventId(event.eventId())
				.topic(KafkaTopicConfig.LOGGED_OUT_TOPIC)
				.partitionKey(sid)
				.payload(payload)
				.createdAt(event.occurredAt())
				.build());
	}
}
