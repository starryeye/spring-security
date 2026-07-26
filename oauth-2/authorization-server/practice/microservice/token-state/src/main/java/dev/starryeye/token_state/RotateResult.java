package dev.starryeye.token_state;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RotateResult(
		RotateStatus status,
		String sub,
		String scope,
		long authTime,
		String refreshToken,
		long expiresAt
) {

	public static RotateResult failed(RotateStatus status) {
		return new RotateResult(status, null, null, 0L, null, 0L);
	}
}
