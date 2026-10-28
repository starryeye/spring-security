package dev.starryeye.audit.query;

import dev.starryeye.audit.jpa.RefreshTokenEventEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenFamilyViewTest {

	/**
	 * 한 계열의 사건 목록에서 계열의 요약(발급 시각 · 절대 상한 · 재구성한 상태 · 요약 사유 · 재사용 표지)을 만드는
	 *      규칙을 스프링 없이 확인한다. 사건은 저장소가 돌려주는 순서(occurredAt 오름차순, 같으면 id 오름차순)로 넘긴다.
	 *
	 * 주의. 엔티티는 저장하지 않고 builder 로만 만든다. 그래서 id 는 null 이다 — from 은 넘겨받은 순서를 그대로 믿고
	 *      스스로 다시 정렬하지 않으므로 id 가 없어도 된다.
	 */

	private static final Instant T0 = Instant.parse("2026-10-28T00:00:00Z");
	private static final Duration TTL = Duration.ofDays(14);
	private static final Duration FAMILY_CAP = Duration.ofDays(30);

	@Test
	@DisplayName("회전만 있는 계열은 ACTIVE 다 — 발급 시각과 절대 상한은 ISSUED 의 값이다")
	void familyWithRotationsIsActive() {
		List<RefreshTokenEventEntity> events = List.of(
				issued("E-1", T0, T0.plus(TTL), T0.plus(FAMILY_CAP)),
				rotated("E-2", T0.plus(Duration.ofHours(1)), T0.plus(Duration.ofHours(1)).plus(TTL)));

		RefreshTokenFamilyView view = RefreshTokenFamilyView.from(events, T0.plus(Duration.ofHours(2)));

		assertThat(view.familyId()).isEqualTo("FAM-1");
		assertThat(view.clientId()).isEqualTo("demo-rp");
		assertThat(view.sid()).isEqualTo("SID-A");
		assertThat(view.reconstructedState()).isEqualTo("ACTIVE");
		assertThat(view.issuedAt()).isEqualTo(T0);
		assertThat(view.familyExpiresAt()).isEqualTo(T0.plus(FAMILY_CAP));
		assertThat(view.revokedReason()).isNull();
		assertThat(view.reuseDetected()).isFalse();
		assertThat(view.events()).extracting(RefreshTokenEventView::eventId).containsExactly("E-1", "E-2");
	}

	@Test
	@DisplayName("REVOKED 사건이 하나라도 있으면 그 뒤에 ROTATED 가 있어도 REVOKED 다")
	void anyRevokedEventMakesTheFamilyRevokedWhateverItsPosition() {
		List<RefreshTokenEventEntity> events = List.of(
				issued("E-1", T0, T0.plus(TTL), T0.plus(FAMILY_CAP)),
				revoked("E-2", T0.plus(Duration.ofHours(1)), "REUSE_DETECTED"),
				rotated("E-3", T0.plus(Duration.ofHours(1)).plusMillis(1), T0.plus(Duration.ofHours(1)).plusMillis(1).plus(TTL)));

		RefreshTokenFamilyView view = RefreshTokenFamilyView.from(events, T0.plus(Duration.ofHours(2)));

		assertThat(view.reconstructedState()).isEqualTo("REVOKED");
	}

	@Test
	@DisplayName("요약 사유는 첫 REVOKED 의 사유이고, 뒤에 온 REUSE_DETECTED 도 reuseDetected 로 드러난다")
	void firstRevocationIsTheSummaryAndReuseIsFlagged() {
		List<RefreshTokenEventEntity> events = List.of(
				issued("E-1", T0, T0.plus(TTL), T0.plus(FAMILY_CAP)),
				rotated("E-2", T0.plus(Duration.ofHours(1)), T0.plus(Duration.ofHours(1)).plus(TTL)),
				revoked("E-3", T0.plus(Duration.ofMinutes(90)), "SESSION_LOGGED_OUT"),
				revoked("E-4", T0.plus(Duration.ofMinutes(100)), "REUSE_DETECTED"));

		RefreshTokenFamilyView view = RefreshTokenFamilyView.from(events, T0.plus(Duration.ofHours(2)));

		assertThat(view.reconstructedState()).isEqualTo("REVOKED");
		assertThat(view.revokedReason()).isEqualTo("SESSION_LOGGED_OUT");
		assertThat(view.reuseDetected()).isTrue();
	}

	/**
	 * 주의. 판정은 마지막 ISSUED/ROTATED 의 expiresAt 으로 한다. asOf = E 일 때 ISSUED 의 expiresAt(T0+14d)은 이미
	 *      지났으므로, 마지막이 아니라 아무 토큰의 expiresAt 이나 보는 구현이면 여기서 EXPIRED 가 나온다.
	 */
	@Test
	@DisplayName("마지막 토큰의 expiresAt 이 지나면 EXPIRED 다 — 같은 시각은 아직 만료가 아니다")
	void expiredWhenTheLastTokenExpiryHasPassed() {
		Instant lastExpiresAt = T0.plus(Duration.ofHours(1)).plus(TTL);
		List<RefreshTokenEventEntity> events = List.of(
				issued("E-1", T0, T0.plus(TTL), T0.plus(FAMILY_CAP)),
				rotated("E-2", T0.plus(Duration.ofHours(1)), lastExpiresAt));

		assertThat(RefreshTokenFamilyView.from(events, lastExpiresAt.plusSeconds(1)).reconstructedState()).isEqualTo("EXPIRED");
		assertThat(RefreshTokenFamilyView.from(events, lastExpiresAt).reconstructedState()).isEqualTo("ACTIVE");
	}

	/**
	 * 주의. 첫 계열은 token-state 가 지금 설정에서 내는 모양이다 — 회전한 토큰의 expiresAt 이 familyExpiresAt 으로
	 *      잘려 둘이 같으므로, 마지막 토큰 조건만으로도 EXPIRED 가 된다. 상한 조건 자체를 고정하는 것은 둘째 계열이다.
	 *      token-state 의 issue 는 발급 토큰의 expiresAt 을 상한으로 자르지 않으므로, 수명 설정이 상한보다 길면 ISSUED
	 *      하나뿐인 계열의 expiresAt(T0+40d)이 familyExpiresAt(T0+30d)보다 뒤일 수 있다. 그때 상한이 지나면 EXPIRED,
	 *      상한과 같은 시각은 아직 ACTIVE 다.
	 */
	@Test
	@DisplayName("계열의 절대 상한이 지나면 EXPIRED 다 — 마지막 토큰의 expiresAt 이 아직 남아 있어도")
	void expiredWhenTheFamilyCapHasPassed() {
		List<RefreshTokenEventEntity> events = List.of(
				issued("E-1", T0, T0.plus(TTL), T0.plus(FAMILY_CAP)),
				rotated("E-2", T0.plus(Duration.ofDays(20)), T0.plus(FAMILY_CAP)));

		RefreshTokenFamilyView view = RefreshTokenFamilyView.from(events, T0.plus(FAMILY_CAP).plusSeconds(1));

		assertThat(view.reconstructedState()).isEqualTo("EXPIRED");

		List<RefreshTokenEventEntity> issuedBeyondCap = List.of(
				issued("E-1", T0, T0.plus(Duration.ofDays(40)), T0.plus(FAMILY_CAP)));

		assertThat(RefreshTokenFamilyView.from(issuedBeyondCap, T0.plus(FAMILY_CAP).plusSeconds(1)).reconstructedState())
				.isEqualTo("EXPIRED");
		assertThat(RefreshTokenFamilyView.from(issuedBeyondCap, T0.plus(FAMILY_CAP)).reconstructedState())
				.isEqualTo("ACTIVE");
	}

	/**
	 * 주의. ISSUED 사건이 감사 DLT 로 갔거나 보존 기간 밖이라 이 계열의 사건이 ROTATED 하나뿐인 경우다. 계열은
	 *      목록에 그대로 보이고, 발급 시각과 절대 상한은 알 수 없어 null 이며, 만료 판정은 남은 ROTATED 의 expiresAt
	 *      으로만 한다.
	 */
	@Test
	@DisplayName("ISSUED 사건이 없는 계열도 보인다 — 발급 시각·절대 상한은 null, 판정은 남은 ROTATED 로 한다")
	void familyWithoutIssuedEventIsStillShown() {
		Instant expiresAt = T0.plus(Duration.ofHours(1)).plus(TTL);
		List<RefreshTokenEventEntity> events = List.of(
				event("E-2", "ROTATED", T0.plus(Duration.ofHours(1)), "other-rp", "SID-B", expiresAt, null, null));

		RefreshTokenFamilyView active = RefreshTokenFamilyView.from(events, expiresAt.minusSeconds(1));
		RefreshTokenFamilyView expired = RefreshTokenFamilyView.from(events, expiresAt.plusSeconds(1));

		assertThat(active.reconstructedState()).isEqualTo("ACTIVE");
		assertThat(expired.reconstructedState()).isEqualTo("EXPIRED");
		assertThat(active.issuedAt()).isNull();
		assertThat(active.familyExpiresAt()).isNull();
		assertThat(active.clientId()).isEqualTo("other-rp");
		assertThat(active.sid()).isEqualTo("SID-B");
		assertThat(active.events()).extracting(RefreshTokenEventView::eventId).containsExactly("E-2");
	}

	@Test
	@DisplayName("모르는 kind 는 사건 목록에만 들어가고 상태 판정을 바꾸지 않는다")
	void unknownKindDoesNotChangeTheState() {
		List<RefreshTokenEventEntity> events = List.of(
				issued("E-1", T0, T0.plus(TTL), T0.plus(FAMILY_CAP)),
				event("E-2", "SUSPENDED", T0.plus(Duration.ofHours(1)), "demo-rp", "SID-A", null, null, null));

		RefreshTokenFamilyView view = RefreshTokenFamilyView.from(events, T0.plus(Duration.ofHours(2)));

		assertThat(view.reconstructedState()).isEqualTo("ACTIVE");
		assertThat(view.events()).extracting(RefreshTokenEventView::kind).containsExactly("ISSUED", "SUSPENDED");
	}

	private static RefreshTokenEventEntity issued(String eventId, Instant at, Instant expiresAt, Instant familyExpiresAt) {
		return event(eventId, "ISSUED", at, "demo-rp", "SID-A", expiresAt, familyExpiresAt, null);
	}

	private static RefreshTokenEventEntity rotated(String eventId, Instant at, Instant expiresAt) {
		return event(eventId, "ROTATED", at, "demo-rp", "SID-A", expiresAt, null, null);
	}

	private static RefreshTokenEventEntity revoked(String eventId, Instant at, String reason) {
		return event(eventId, "REVOKED", at, "demo-rp", "SID-A", null, null, reason);
	}

	private static RefreshTokenEventEntity event(String eventId, String kind, Instant at, String clientId, String sid,
			Instant expiresAt, Instant familyExpiresAt, String reason) {
		boolean revoked = "REVOKED".equals(kind);
		return RefreshTokenEventEntity.builder()
				.eventId(eventId)
				.eventType("oidc.refresh-token.lifecycle.v1")
				.kind(kind)
				.occurredAt(at)
				.recordedAt(at)
				.sub("user-sub-0001")
				.clientId(clientId)
				.familyId("FAM-1")
				.sid(sid)
				.reason(reason)
				.revokedTokens(revoked ? 2 : null)
				.activeTokensRevoked(revoked ? 1 : null)
				.expiresAt(expiresAt)
				.familyExpiresAt(familyExpiresAt)
				.payload("{}")
				.build();
	}
}
