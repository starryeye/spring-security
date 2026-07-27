package dev.starryeye.token.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RotateResult(
		String status,
		String sub,
		String scope,
		long authTime,
		String refreshToken,
		long expiresAt
) {

	public boolean isRotated() {
		return "ROTATED".equals(status);
	}

	/**
	 * 축소 요청이 저장된 grant 를 벗어났다는 뜻이다. 이 status 만 invalid_scope 로 나가고 나머지 실패는 전부
	 *      invalid_grant 로 뭉갠다. 이 경우 token-state 는 아무 상태도 바꾸지 않았으므로 원래 토큰이 아직 살아 있다.
	 */
	public boolean isScopeExceeded() {
		return "SCOPE_EXCEEDED".equals(status);
	}
}
