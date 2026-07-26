package dev.starryeye.token_state;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntrospectResult(boolean active, String sub, String clientId, String scope, long exp, long iat) {

	public static IntrospectResult inactive() {
		return new IntrospectResult(false, null, null, null, 0L, 0L);
	}
}
