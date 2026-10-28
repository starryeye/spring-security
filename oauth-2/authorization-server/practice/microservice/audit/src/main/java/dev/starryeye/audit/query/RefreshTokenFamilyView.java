package dev.starryeye.audit.query;

import dev.starryeye.audit.jpa.RefreshTokenEventEntity;

import java.time.Instant;
import java.util.List;

/**
 * refresh token 계열 하나의 요약과 그 계열의 사건 목록.
 *
 * 주의. reconstructedState 는 감사 이력으로 재구성한 값이다. 진실의 출처는 token-state 의 refresh_tokens 이고,
 *      이 값은 token-state 의 outbox 폴링과 이 서비스의 소비만큼 늦다. 이 이름이 status 가 아니라
 *      reconstructedState 인 이유다 — 응답이 "지금 token-state 의 상태"처럼 읽히지 않게 한다.
 *
 * 주의. REVOKED 사건이 하나라도 있으면 사건의 순서와 무관하게 REVOKED 다. token-state 에서 끊긴 계열은 다시
 *      회전하지 않으므로 REVOKED 는 되돌릴 수 없는 종착 상태다. token-state 는 시각을 계열 잠금 뒤에 재서 사건 순서를
 *      맞추지만(RefreshTokenService.rotate 의 주의 참고) 서버 사이의 시계 차이는 그 규칙 밖이다. 그런 이유로 REVOKED
 *      뒤에 ROTATED 가 놓여도 "끊긴 계열이 살아 있다"고 보여 주지 않는다.
 *
 * 주의. revokedReason 은 첫 REVOKED 의 reason 이다 — 그 계열을 쓸 수 없게 된 순간이다. reuseDetected 는 reason 이
 *      REUSE_DETECTED 인 REVOKED 가 하나라도 있는가다. 로그아웃으로 먼저 끊긴 뒤 재사용이 탐지돼도 도난 신호가 요약에서
 *      가려지지 않게 둘을 따로 낸다. 세부는 events 에 전부 있다.
 *
 * 주의. REVOKED 가 없으면 마지막 ISSUED/ROTATED 의 expiresAt 이나 계열의 familyExpiresAt 이 asOf 보다 앞일 때
 *      EXPIRED, 아니면 ACTIVE 다. 비교는 token-state 의 isExpired 와 같은 isBefore 라 asOf 와 같은 시각은 아직 만료가
 *      아니다. 값이 없는 쪽은 판정에서 빠지므로, 판정할 값이 하나도 없는 계열은 ACTIVE 로 나온다.
 *      token-state 는 회전한 토큰의 expiresAt 을 familyExpiresAt 으로 자르고(RefreshTokenService.rotate) 발급 토큰의
 *      수명(14일)을 계열 상한(30일)보다 짧게 설정해 두므로, 지금 설정에서 낸 사건만으로는 familyExpiresAt 조건이 결과를
 *      바꾸지 않는다. 그래도 isExpired 와 같은 두 조건을 그대로 둔다 — 수명 설정이 상한보다 길어져도 상한을 넘긴
 *      계열이 ACTIVE 로 보이지 않게.
 *
 * 주의. issuedAt · familyExpiresAt 은 ISSUED 사건의 값이다. ISSUED 가 감사 DLT 로 갔거나 보존 기간 밖이라 없으면 둘 다
 *      null 이고, 계열은 그래도 보인다. clientId · sid 는 첫 사건의 값이다.
 *
 * 주의. ISSUED · ROTATED · REVOKED 가 아닌 kind 는 events 에만 들어가고 판정에 쓰이지 않는다(같은 v1 계약에 종류가
 *      더해진 경우 — RefreshTokenEventEntity 의 kind 문단 참고).
 */
public record RefreshTokenFamilyView(
		String familyId,
		String clientId,
		String sid,
		Instant issuedAt,
		Instant familyExpiresAt,
		String reconstructedState,
		String revokedReason,
		boolean reuseDetected,
		List<RefreshTokenEventView> events
) {

	static final String ISSUED = "ISSUED";
	static final String ROTATED = "ROTATED";
	static final String REVOKED = "REVOKED";
	static final String REUSE_DETECTED = "REUSE_DETECTED";

	static final String STATE_ACTIVE = "ACTIVE";
	static final String STATE_EXPIRED = "EXPIRED";
	static final String STATE_REVOKED = "REVOKED";

	/**
	 * 한 계열의 사건으로 요약을 만든다.
	 *
	 * @param eventsInOrder 같은 familyId 의 사건. occurredAt 오름차순, 같으면 id 오름차순으로 이미 정렬돼 있어야 하고
	 *                      비어 있으면 안 된다. 다시 정렬하지 않는다.
	 * @param asOf          만료 판정의 기준 시각
	 */
	public static RefreshTokenFamilyView from(List<RefreshTokenEventEntity> eventsInOrder, Instant asOf) {
		RefreshTokenEventEntity first = eventsInOrder.get(0);

		RefreshTokenEventEntity issued = null;
		RefreshTokenEventEntity lastToken = null;
		RefreshTokenEventEntity firstRevoked = null;
		boolean reuseDetected = false;

		for (RefreshTokenEventEntity event : eventsInOrder) {
			String kind = event.getKind();
			if (ISSUED.equals(kind)) {
				if (issued == null) {
					issued = event;
				}
				lastToken = event;
			} else if (ROTATED.equals(kind)) {
				lastToken = event;
			} else if (REVOKED.equals(kind)) {
				if (firstRevoked == null) {
					firstRevoked = event;
				}
				if (REUSE_DETECTED.equals(event.getReason())) {
					reuseDetected = true;
				}
			}
			// 그 밖의 kind 는 아래 events 에만 들어간다
		}

		Instant issuedAt = (issued == null) ? null : issued.getOccurredAt();
		Instant familyExpiresAt = (issued == null) ? null : issued.getFamilyExpiresAt();

		String state;
		if (firstRevoked != null) {
			state = STATE_REVOKED;
		} else if (isBefore(lastToken == null ? null : lastToken.getExpiresAt(), asOf)
				|| isBefore(familyExpiresAt, asOf)) {
			state = STATE_EXPIRED;
		} else {
			state = STATE_ACTIVE;
		}

		return new RefreshTokenFamilyView(
				first.getFamilyId(),
				first.getClientId(),
				first.getSid(),
				issuedAt,
				familyExpiresAt,
				state,
				(firstRevoked == null) ? null : firstRevoked.getReason(),
				reuseDetected,
				eventsInOrder.stream().map(RefreshTokenEventView::from).toList());
	}

	private static boolean isBefore(Instant expiresAt, Instant asOf) {
		return expiresAt != null && expiresAt.isBefore(asOf);
	}
}
