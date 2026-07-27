package dev.starryeye.token_state;

public enum RotateStatus {

	/**
	 * 회전 판정 결과다.
	 *
	 * 주의. SCOPE_EXCEEDED 만 성격이 다르다. 나머지는 "이 토큰을 쓸 수 없다" 이고 호출자가 전부 invalid_grant 로
	 *      뭉개지만, SCOPE_EXCEEDED 는 토큰은 멀쩡하고 요청이 잘못된 경우라 invalid_scope 로 나간다.
	 *      그래서 이 값이 나올 때는 어떤 상태도 바뀌지 않아야 한다 — 토큰을 소진시켜 놓고 요청을 거절하면
	 *      오타 한 번이 grant 를 통째로 파괴한다.
	 */
	ROTATED, REUSE_DETECTED, REVOKED, EXPIRED, NOT_FOUND, CLIENT_MISMATCH, SCOPE_EXCEEDED
}
