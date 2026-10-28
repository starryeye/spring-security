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
	 *      .v2 토픽이 생겨 이 소비자가 구독을 추가하면(그 토픽의 DLT NewTopic 선언도 함께 — KafkaConsumerConfig
	 *      참고) event_type 파생이 두 계약을 감사 기록에서 자동으로 구분한다. 구독을 추가하기 전에는 .v2 이벤트가
	 *      감사에 들어오지 않는다.
	 *
	 * 주의. 페이로드를 발행자의 record 타입으로 역직렬화하지 않고 JsonNode 로 읽는다. 감사는 이벤트마다
	 *      모양을 알 필요가 없고 공통 머리(eventId·sid·sub·occurredAt)만 뽑으면 된다. 원문은 받은 문자열을
	 *      그대로 저장하므로 발행자가 필드를 더해도 손실이 없다.
	 *
	 * 주의. eventId · sid · occurredAt 은 필수이고 sub 는 선택이다. 필수가 없거나(키가 없거나 JSON null), 값이 있는데
	 *      모양이 틀리면(문자열이 아니거나 비었거나, 시각 형식이 아니면) JsonFields 가 예외를 던지고, 컨테이너가
	 *      재시도하다 감사 DLT 로 보낸다. sub 는 없으면 null 로 기록하되 있는데 문자열이 아니거나 비었으면 같은 길로 간다.
	 *      JsonNode.asText() 로 읽으면 {"sid":""} 의 빈 값이 not null 검사를 통과해 빈 sid 로 기록된다(JsonFields 참고).
	 *      기록기의 not null 위반은 소비자가 거르지 못한 값에 대한 마지막 방어선으로 남고, 기록기는 그것을 흡수하지
	 *      않고 다시 던진다. 여기서 기본값을 채우거나 예외를 삼키면 감사가 기록을 조용히 잃는다.
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
		AuditRecord record = new AuditRecord(
				JsonFields.requiredText(node, "eventId"),
				topic,
				JsonFields.requiredInstant(node, "occurredAt"),
				JsonFields.requiredText(node, "sid"),
				JsonFields.optionalText(node, "sub"),
				payload);
		boolean recorded = recorder.record(record);
		log.debug("audit event: type={} eventId={} recorded={}", topic, record.eventId(), recorded);
	}
}
