package dev.starryeye.audit.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.audit.RefreshTokenEventRecord;
import dev.starryeye.audit.RefreshTokenEventRecorder;
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
public class RefreshTokenEventConsumer {

	/**
	 * refresh token 계열 사건(ISSUED · ROTATED · REVOKED)을 refresh_token_events 에 받아 적는다.
	 *
	 * 주의. 이 리스너는 AuditEventConsumer 와 같은 컨슈머 그룹(my.consumer-group-id)을 쓴다. 같은 audit 그룹의 다른
	 *      리스너이고, 오프셋은 토픽(파티션)마다 따로 커밋된다. token-state 의 그룹과는 여전히 다르다. 실패한 편지는
	 *      이 토픽 이름의 감사 DLT 로 간다(KafkaConsumerConfig).
	 *
	 * 주의. event_type 은 AuditEventConsumer 와 같은 규칙으로 수신한 토픽 이름이다. 사건의 종류는 페이로드의 kind 에 있다.
	 *
	 * 주의. 필수 필드는 eventId · kind · occurredAt · familyId · sub · clientId 이고, 선택 필드는 sid · expiresAt ·
	 *      familyExpiresAt · reason · revokedTokens · activeTokensRevoked 다. 필수가 없거나 모양이 틀린 값(문자열이
	 *      아니거나 비었거나, 정수가 아니거나 int 범위를 넘거나, 시각 형식이 아닌 값)은 JsonFields 가 예외로 거절하고,
	 *      컨테이너가 재시도한 뒤 감사 DLT 로 보낸다. 값을 문자열로 바꾸거나 기본값으로 채워 기록하지 않는다.
	 *
	 * 주의. kind 의 값은 검사하지 않는다. 같은 v1 계약에 종류가 더해져도 모르는 종류를 기록한다. 다만 컬럼 길이(30)는
	 *      넘을 수 없다(RefreshTokenEventEntity 참고). 이 테이블을 읽는 쪽은 모르는 kind 를 계열의 상태 판정에 쓰지
	 *      말아야 한다.
	 *
	 * 주의. 이 토픽의 sub 는 사실이다. 발행자(token-state)가 자기 refresh_tokens 행에서 읽어 싣는다. 로그아웃 토픽에서
	 *      session 이 보고만 하는 sub(reported_sub)와 다르다.
	 *
	 * 주의. 페이로드를 JsonNode 로 읽고 원문 문자열을 그대로 저장한다(AuditEventConsumer 와 같은 이유). 발행자가 필드를
	 *      더해도 손실이 없다.
	 *
	 * 주의. 예외를 잡지 않는다. 삼키면 처리하지 못한 이벤트의 오프셋이 커밋돼 그 기록이 영원히 사라진다.
	 */

	public static final String LIFECYCLE_TOPIC = "oidc.refresh-token.lifecycle.v1";

	private final RefreshTokenEventRecorder recorder;
	private final ObjectMapper objectMapper;

	@KafkaListener(topics = LIFECYCLE_TOPIC, groupId = "${my.consumer-group-id}")
	public void onEvent(@Payload String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) throws Exception {
		JsonNode node = objectMapper.readTree(payload);
		RefreshTokenEventRecord record = new RefreshTokenEventRecord(
				JsonFields.requiredText(node, "eventId"),
				topic,
				JsonFields.requiredText(node, "kind"),
				JsonFields.requiredInstant(node, "occurredAt"),
				JsonFields.requiredText(node, "familyId"),
				JsonFields.requiredText(node, "sub"),
				JsonFields.requiredText(node, "clientId"),
				JsonFields.optionalText(node, "sid"),
				JsonFields.optionalInstant(node, "expiresAt"),
				JsonFields.optionalInstant(node, "familyExpiresAt"),
				JsonFields.optionalText(node, "reason"),
				JsonFields.optionalInteger(node, "revokedTokens"),
				JsonFields.optionalInteger(node, "activeTokensRevoked"),
				payload);
		boolean recorded = recorder.record(record);
		log.debug("refresh token event: kind={} eventId={} familyId={} recorded={}",
				record.kind(), record.eventId(), record.familyId(), recorded);
	}
}
