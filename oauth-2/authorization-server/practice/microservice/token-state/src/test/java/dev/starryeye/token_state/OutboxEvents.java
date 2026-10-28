package dev.starryeye.token_state;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.token_state.jpa.OutboxEntity;
import dev.starryeye.token_state.jpa.OutboxEntityRepository;

import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;

public final class OutboxEvents {

	/**
	 * outbox 에 쌓인 사건을 계열(familyId) 하나로 걸러 읽는 테스트 도우미다. 결과는 id 오름차순, 곧 쓴 순서다.
	 *
	 * 주의. 이 모듈의 h2(jdbc:h2:mem:token-state)는 이름 있는(named) 인메모리 DB 라, 프로퍼티가 달라 컨텍스트
	 *      캐시가 갈린 다른 @SpringBootTest 도 프로세스 안에서는 같은 outbox 테이블을 공유한다. 그래서
	 *      테이블 전체를 세지 않고, 단언은 언제나 이 계열 하나(UUID)로 걸러서 한다.
	 *
	 * 주의. 거름 기준은 partition_key 컬럼이 아니라 payload 안의 familyId 문자열이다. partition_key 로 거르면
	 *      키를 잘못 쓴 구현에서는 행이 통째로 걸러져 없어지므로, 키를 확인하는 단언에 닿기 전에 개수 단언이
	 *      먼저 깨진다. UUID 는 다른 필드와 겹칠 수 없어 문자열 포함으로 걸러도 다른 계열이 섞이지 않는다.
	 */

	private OutboxEvents() {
	}

	public static List<OutboxEntity> rowsOf(OutboxEntityRepository repository, String familyId) {
		return repository.findAll().stream()
				.filter(row -> row.getPayload().contains(familyId))
				.sorted(Comparator.comparing(OutboxEntity::getId))
				.toList();
	}

	public static List<JsonNode> eventsOf(OutboxEntityRepository repository, ObjectMapper objectMapper, String familyId) {
		return rowsOf(repository, familyId).stream()
				.map(row -> parse(objectMapper, row))
				.toList();
	}

	public static List<String> kindsOf(OutboxEntityRepository repository, ObjectMapper objectMapper, String familyId) {
		return eventsOf(repository, objectMapper, familyId).stream()
				.map(event -> event.get("kind").asText())
				.toList();
	}

	private static JsonNode parse(ObjectMapper objectMapper, OutboxEntity row) {
		try {
			return objectMapper.readTree(row.getPayload());
		} catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
	}
}
