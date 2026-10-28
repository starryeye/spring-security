package dev.starryeye.audit.query;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.audit.jpa.RefreshTokenEventEntity;
import dev.starryeye.audit.jpa.RefreshTokenEventEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
		"spring.datasource.url=jdbc:h2:mem:audit-query;DB_CLOSE_DELAY=-1;MODE=MySQL",
		"spring.kafka.listener.auto-startup=false"   // 리스너 컨테이너만 끈다. 실제 브로커가 떠 있어도 refresh 계열 사건을 받아 적지 않게
})
@AutoConfigureMockMvc
class RefreshTokenAuditControllerTest {

	/**
	 * 조회 API 가 한 sub 의 사건을 계열별로 묶어, 계열은 최근 발급이 위로, 계열 안의 사건은 시간순으로 내는지와
	 *      응답 JSON 의 키가 응답 record 의 필드 이름 그대로인지를 확인한다.
	 *
	 * 주의. 요청에 jwt() 로 SCOPE_audit.read 권한을 직접 심는다. 토큰의 서명 · iss · exp · typ 검증은 이 클래스가 보지
	 *      않는다 — 그것은 security 패키지의 테스트가 실제 서명한 토큰으로 본다.
	 *
	 * 주의. datasource url 을 이 클래스 전용으로 준다(AuditRecorderTest 와 같은 이유). 리스너를 끄는 이유도 같다.
	 */

	private static final String SUB = "user-sub-0001";
	private static final Instant TEN = Instant.parse("2026-10-28T10:00:00Z");

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private RefreshTokenEventEntityRepository repository;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void clean() {
		repository.deleteAll();
	}

	/**
	 * 주의. 같은 10:05 의 ROTATED 둘 가운데 A-3 을 B · C 보다도 나중에 저장한다. 같은 시각인 A-2 · A-3 은 id 순으로
	 *      나오고, 다른 계열의 사건이 사이에 저장돼도 계열 안에 끼어들지 않는다. 계열은 A 가 먼저 저장됐어도 최근
	 *      발급(B, 11:00)이 위로 온다.
	 */
	@Test
	@DisplayName("계열은 최근 발급이 위, 계열 안의 사건은 시간순(같은 시각은 id 순)이고 다른 sub 는 섞이지 않는다")
	void familiesAreNewestFirstAndEventsInTimeOrder() throws Exception {
		save("A-1", SUB, "FAM-A", "ISSUED", TEN);
		save("A-2", SUB, "FAM-A", "ROTATED", TEN.plus(Duration.ofMinutes(5)));
		save("B-1", SUB, "FAM-B", "ISSUED", TEN.plus(Duration.ofHours(1)));
		save("C-1", "user-sub-0002", "FAM-C", "ISSUED", TEN.plus(Duration.ofHours(2)));
		save("A-3", SUB, "FAM-A", "ROTATED", TEN.plus(Duration.ofMinutes(5)));

		String body = mockMvc.perform(get("/audit/users/{sub}/refresh-token-families", SUB).with(auditRead()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sub").value(SUB))
				.andExpect(jsonPath("$.asOf").exists())
				.andExpect(jsonPath("$.families", hasSize(2)))
				.andExpect(jsonPath("$.families[0].familyId").value("FAM-B"))
				.andExpect(jsonPath("$.families[1].familyId").value("FAM-A"))
				.andExpect(jsonPath("$.families[1].events[*].eventId", contains("A-1", "A-2", "A-3")))
				.andReturn().getResponse().getContentAsString();

		// 값이 null 인 키도 빠지지 않는다 — 응답 JSON 의 키는 응답 record 의 필드 이름 그대로다.
		JsonNode familyA = objectMapper.readTree(body).path("families").path(1);
		assertThat(fieldNames(familyA)).containsExactlyInAnyOrder("familyId", "clientId", "sid", "issuedAt",
				"familyExpiresAt", "reconstructedState", "revokedReason", "reuseDetected", "events");
		assertThat(fieldNames(familyA.path("events").path(0))).containsExactlyInAnyOrder("eventId", "kind",
				"occurredAt", "recordedAt", "expiresAt", "familyExpiresAt", "reason", "revokedTokens",
				"activeTokensRevoked");
	}

	@Test
	@DisplayName("사건이 없는 sub 는 200 과 빈 목록이다")
	void unknownSubIsAnEmptyList() throws Exception {
		mockMvc.perform(get("/audit/users/{sub}/refresh-token-families", "nobody").with(auditRead()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.families", hasSize(0)));
	}

	private static RequestPostProcessor auditRead() {
		return jwt().authorities(new SimpleGrantedAuthority("SCOPE_audit.read"));
	}

	private static List<String> fieldNames(JsonNode node) {
		List<String> names = new ArrayList<>();
		node.fieldNames().forEachRemaining(names::add);
		return names;
	}

	private void save(String eventId, String sub, String familyId, String kind, Instant occurredAt) {
		boolean issued = "ISSUED".equals(kind);
		repository.save(RefreshTokenEventEntity.builder()
				.eventId(eventId)
				.eventType("oidc.refresh-token.lifecycle.v1")
				.kind(kind)
				.occurredAt(occurredAt)
				.recordedAt(Instant.now())
				.sub(sub)
				.clientId("demo-rp")
				.familyId(familyId)
				.sid("SID-A")
				.expiresAt(occurredAt.plus(Duration.ofDays(14)))
				.familyExpiresAt(issued ? occurredAt.plus(Duration.ofDays(30)) : null)
				.payload("{}")
				.build());
	}
}
