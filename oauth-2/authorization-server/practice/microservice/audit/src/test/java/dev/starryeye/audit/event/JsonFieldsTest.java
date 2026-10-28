package dev.starryeye.audit.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonFieldsTest {

	/**
	 * JsonFields 가 값을 변환하지 않고 거절하는 규칙을 스프링 없이 고정한다.
	 *
	 * 주의. 거절하는 입력은 JsonNode.asText()·asInt() 가 조용히 바꿔 받는 것들이다. 이 테스트의 입력이 곧 그
	 *      변환의 목록이다(jackson-databind 2.18.3 기준: asText() 는 123 을 "123" 으로, {} 를 ""로 바꾸고,
	 *      asInt() 는 "3" 을 3 으로, 3.5 를 3 으로, 4294967296 을 0 으로 바꾼다).
	 *
	 * 주의. 거절 테스트는 입력마다 따로 단언한다. 한 입력이라도 예외 없이 지나가면 어느 입력인지 설명에 남는다.
	 */

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("requiredText 는 문자열을 그대로 읽는다")
	void requiredTextReadsAString() throws Exception {
		assertThat(JsonFields.requiredText(json("{\"a\":\"x\"}"), "a")).isEqualTo("x");
	}

	@Test
	@DisplayName("requiredText 는 없는 키·JSON null·빈 문자열·공백뿐인 문자열·문자열이 아닌 값을 거절한다")
	void requiredTextRejectsAbsentNullBlankAndNonText() throws Exception {
		assertRejectsAll(JsonFields::requiredText, IllegalArgumentException.class,
				"{}", "{\"a\":null}", "{\"a\":\"\"}", "{\"a\":\"  \"}", "{\"a\":123}", "{\"a\":{}}");
	}

	@Test
	@DisplayName("optionalText 는 없는 키와 JSON null 을 null 로 읽는다")
	void optionalTextIsNullWhenAbsentOrNull() throws Exception {
		assertThat(JsonFields.optionalText(json("{}"), "a")).isNull();
		assertThat(JsonFields.optionalText(json("{\"a\":null}"), "a")).isNull();
	}

	@Test
	@DisplayName("optionalText 는 값이 있으면 빈 문자열과 문자열이 아닌 값을 거절한다")
	void optionalTextRejectsBlankAndNonText() throws Exception {
		assertRejectsAll(JsonFields::optionalText, IllegalArgumentException.class,
				"{\"a\":\"\"}", "{\"a\":true}");
	}

	@Test
	@DisplayName("optionalInteger 는 int 를 읽고, 없는 키와 JSON null 은 null 이다")
	void optionalIntegerReadsAnInt() throws Exception {
		assertThat(JsonFields.optionalInteger(json("{\"a\":3}"), "a")).isEqualTo(3);
		assertThat(JsonFields.optionalInteger(json("{}"), "a")).isNull();
		assertThat(JsonFields.optionalInteger(json("{\"a\":null}"), "a")).isNull();
	}

	@Test
	@DisplayName("optionalInteger 는 문자열·소수·int 범위를 넘는 수를 거절한다")
	void optionalIntegerRejectsTextAndFractions() throws Exception {
		assertRejectsAll(JsonFields::optionalInteger, IllegalArgumentException.class,
				"{\"a\":\"3\"}", "{\"a\":3.5}", "{\"a\":4294967296}");
	}

	@Test
	@DisplayName("requiredInstant 는 ISO-8601 문자열을 Instant 로 읽는다")
	void requiredInstantParsesIso8601() throws Exception {
		assertThat(JsonFields.requiredInstant(json("{\"a\":\"2026-10-28T01:02:03.456789Z\"}"), "a"))
				.isEqualTo(Instant.parse("2026-10-28T01:02:03.456789Z"));
	}

	@Test
	@DisplayName("requiredInstant 는 시각이 아닌 문자열을 DateTimeParseException 으로 거절한다")
	void requiredInstantRejectsGarbage() throws Exception {
		assertThatThrownBy(() -> JsonFields.requiredInstant(json("{\"a\":\"yesterday\"}"), "a"))
				.isInstanceOf(DateTimeParseException.class);
	}

	@Test
	@DisplayName("requiredInstant 는 없는 키·JSON null·문자열이 아닌 값도 거절하고, optionalInstant 는 없으면 null 이다")
	void instantsFollowTheTextRules() throws Exception {
		assertRejectsAll(JsonFields::requiredInstant, IllegalArgumentException.class,
				"{}", "{\"a\":null}", "{\"a\":1790000000}");
		assertThat(JsonFields.optionalInstant(json("{}"), "a")).isNull();
		assertThat(JsonFields.optionalInstant(json("{\"a\":null}"), "a")).isNull();
		assertThat(JsonFields.optionalInstant(json("{\"a\":\"2026-10-28T01:02:03Z\"}"), "a"))
				.isEqualTo(Instant.parse("2026-10-28T01:02:03Z"));
		assertThatThrownBy(() -> JsonFields.optionalInstant(json("{\"a\":\"yesterday\"}"), "a"))
				.isInstanceOf(DateTimeParseException.class);
	}

	@Test
	@DisplayName("IllegalArgumentException 의 메시지에는 필드 이름이 들어 있다")
	void rejectionMessageNamesTheField() throws Exception {
		assertThatThrownBy(() -> JsonFields.requiredText(json("{}"), "familyId"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("familyId");
		assertThatThrownBy(() -> JsonFields.optionalText(json("{\"sid\":\"\"}"), "sid"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sid");
		assertThatThrownBy(() -> JsonFields.optionalInteger(json("{\"revokedTokens\":\"3\"}"), "revokedTokens"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("revokedTokens");
		assertThatThrownBy(() -> JsonFields.requiredInstant(json("{\"occurredAt\":7}"), "occurredAt"))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("occurredAt");
	}

	private void assertRejectsAll(BiFunction<JsonNode, String, ?> reader, Class<? extends Throwable> expected,
			String... inputs) throws Exception {
		for (String input : inputs) {
			JsonNode node = json(input);
			assertThatThrownBy(() -> reader.apply(node, "a"), "입력 %s", input).isInstanceOf(expected);
		}
	}

	private JsonNode json(String text) throws Exception {
		return objectMapper.readTree(text);
	}
}
