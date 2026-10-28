package dev.starryeye.audit.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

final class JsonFields {

	/**
	 * 감사 소비자가 이벤트 JSON 에서 공통 필드를 읽는 규칙. 값을 바꿔 받지 않고, 모양이 틀리면 예외를 던진다.
	 *      던진 예외는 컨테이너가 재시도한 뒤 감사 DLT 로 보낸다(KafkaConsumerConfig).
	 *
	 * 주의. 키가 없는 것과 JSON null 을 같은 "없음"으로 읽는다. 발행자마다 채우지 않은 필드를 싣는 모양이 다르다.
	 *      token-state 의 RefreshTokenLifecycleEvent 는 @JsonInclude(NON_NULL) 이라 키 자체를 빼고, session 의
	 *      SessionLoggedOutEvent 는 @JsonInclude 도 session 의 Jackson 포함 설정도 없어(코드를 읽은 결과다) null 필드를
	 *      JSON null 로 싣는다. 없음이면 required* 는 IllegalArgumentException 을 던지고 optional* 는 null 을 돌려준다.
	 *
	 * 주의. JsonNode.asText()·asInt() 로 읽지 않는다. 둘 다 값을 조용히 바꿔 준다. jackson-databind 2.18.3 기준으로
	 *      asText() 는 123 을 "123" 으로, {} 와 [] 를 빈 문자열로 바꾸고, asInt() 는 "3" 을 3 으로, 3.5 를 3 으로,
	 *      4294967296 을 0 으로 바꾼다. 빈 문자열을 그대로 받으면 not null 검사도 통과하므로 {"sid":""} 가 빈 sid 로
	 *      기록된다. 그래서 값이 있으면 모양을 직접 본다 — 텍스트는 문자열이어야 하고 비어 있으면 안 되며, 정수는 int
	 *      범위의 정수 노드여야 하고, 시각은 Instant.parse 가 받아야 한다.
	 *
	 * 주의. 모양만 본다. 값의 의미(예: kind 가 아는 값인지)는 보지 않는다.
	 *
	 * 주의. 시각이 틀리면 Instant.parse 의 DateTimeParseException 이 그대로 나가 필드 이름이 메시지에 없다. 나머지
	 *      거절은 IllegalArgumentException 이고 메시지에 필드 이름이 들어 있다. 둘 다 같은 길(재시도 후 DLT)로 간다.
	 */

	static String requiredText(JsonNode node, String field) {
		String value = optionalText(node, field);
		if (value == null) {
			throw new IllegalArgumentException("필수 필드 " + field + " 가 없다");
		}
		return value;
	}

	static String optionalText(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isTextual()) {
			throw new IllegalArgumentException("필드 " + field + " 는 문자열이어야 한다(받은 것: " + value.getNodeType() + ")");
		}
		if (value.asText().isBlank()) {
			throw new IllegalArgumentException("필드 " + field + " 는 비어 있으면 안 된다");
		}
		return value.asText();
	}

	static Instant requiredInstant(JsonNode node, String field) {
		return Instant.parse(requiredText(node, field));
	}

	static Instant optionalInstant(JsonNode node, String field) {
		String value = optionalText(node, field);
		return value == null ? null : Instant.parse(value);
	}

	static Integer optionalInteger(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return null;
		}
		if (!value.isInt()) {
			String received = value.isNumber() ? value.getNodeType() + " " + value.numberType() : value.getNodeType().toString();
			throw new IllegalArgumentException("필드 " + field + " 는 int 범위의 정수여야 한다(받은 것: " + received + ")");
		}
		return value.intValue();
	}

	private JsonFields() {
	}
}
