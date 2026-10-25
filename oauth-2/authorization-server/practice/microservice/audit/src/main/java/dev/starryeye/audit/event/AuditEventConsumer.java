package dev.starryeye.audit.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.audit.AuditRecord;
import dev.starryeye.audit.AuditRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventConsumer {

	/**
	 * 이벤트를 감사 레코드로 받아 적는다.
	 *
	 * 주의. event_type 은 페이로드가 아니라 수신한 토픽 이름에서 온다. 지금 페이로드에는 타입 필드가 없고,
	 *      토픽 이름이 곧 타입이다. 이렇게 하면 발행자(session)를 한 줄도 고치지 않고 이 소비자를 붙일 수 있다 —
	 *      그것이 이 슬라이스가 증명하려는 것이다. 토픽 이름을 버전(.v1)까지 그대로 넣으므로, 판단이 바뀌어
	 *      .v2 토픽이 생기면 감사 기록에서 두 계약이 자동으로 구분된다.
	 *
	 * 주의. 페이로드를 발행자의 record 타입으로 역직렬화하지 않고 JsonNode 로 읽는다. 감사는 이벤트마다
	 *      모양을 알 필요가 없고 공통 머리(eventId·sid·sub·occurredAt)만 뽑으면 된다. 원문은 받은 문자열을
	 *      그대로 저장하므로 발행자가 필드를 더해도 손실이 없다.
	 *
	 * 주의. 없는 필드는 null 로 넘긴다. sid 나 eventId 가 빠진 기형 이벤트는 기록기에서 not null 위반을 내고,
	 *      기록기는 그것을 흡수하지 않고 다시 던진다 — 그러면 컨테이너가 재시도하다 감사 DLT 로 보낸다.
	 *      여기서 기본값을 채우거나 예외를 삼키면 감사가 기록을 조용히 잃는다.
	 *
	 * 주의. 예외를 잡지 않는다(token-state 의 소비자와 같은 이유). 삼키면 처리하지 못한 이벤트의 오프셋이
	 *      커밋돼 그 기록이 영원히 사라진다.
	 *
	 * 주의. groupId 는 my.consumer-group-id 로 뺀다. 운영 "audit", 테스트 "audit-test" — token-state 의 그룹과
	 *      다르므로 두 소비자의 오프셋은 서로 독립이다. 감사가 멈춰도 폐기는 계속된다.
	 */

	public static final String LOGGED_OUT_TOPIC = "oidc.session.logged-out.v1";

	private final AuditRecorder recorder;
	private final ObjectMapper objectMapper;

	@KafkaListener(topics = LOGGED_OUT_TOPIC, groupId = "${my.consumer-group-id}")
	public void onEvent(@Payload String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws Exception {
		JsonNode node = objectMapper.readTree(payload);
		String occurredAt = text(node, "occurredAt");
		AuditRecord record = new AuditRecord(
				text(node, "eventId"),
				topic,
				occurredAt == null ? null : Instant.parse(occurredAt),
				text(node, "sid"),
				text(node, "sub"),
				payload);
		boolean recorded = recorder.record(record);
		log.debug("audit event: type={} eventId={} recorded={}", topic, record.eventId(), recorded);
	}

	private static String text(JsonNode node, String field) {
		JsonNode value = node.get(field);
		return (value == null || value.isNull()) ? null : value.asText();
	}
}
