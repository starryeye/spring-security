package dev.starryeye.audit.query;

import java.time.Instant;
import java.util.List;

/**
 * GET /audit/users/{sub}/refresh-token-families 의 응답. 키는 이 record 와 RefreshTokenFamilyView ·
 *      RefreshTokenEventView 의 필드 이름 그대로다.
 *
 * 주의. asOf 는 각 계열의 reconstructedState 를 판정한 기준 시각이다. 그 상태는 감사 이력으로 재구성한 값이라
 *      token-state 보다 늦다(RefreshTokenFamilyView 참고).
 *
 * 주의. 사건이 하나도 없는 sub 는 families 가 빈 목록이다. 감사는 사용자가 존재하는지 모른다.
 */
public record RefreshTokenFamiliesResponse(
		String sub,
		Instant asOf,
		List<RefreshTokenFamilyView> families
) {
}
