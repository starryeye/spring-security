package dev.starryeye.token_state.event;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RefreshTokenLifecycleEvent(
		String eventId,
		String kind,
		Instant occurredAt,
		String familyId,
		String sub,
		String clientId,
		String sid,
		Instant expiresAt,
		Instant familyExpiresAt,
		String reason,
		Integer revokedTokens,
		Integer activeTokensRevoked
) {

	/**
	 * refresh token 계열(family)에서 일어난 사건 하나. 토픽 oidc.refresh-token.lifecycle.v1 의 메시지 본문이다.
	 *      kind 가 ISSUED · ROTATED · REVOKED 중 무엇이냐에 따라 채우는 필드가 다르고, 채우지 않은 필드는 JSON 에서
	 *      키 자체가 빠진다.
	 *
	 * 주의. 클래스 수준 @JsonInclude(NON_NULL) 때문에 sid 가 null 이면 키가 빠진다. 읽는 쪽은 "키가 없다"와
	 *      JSON null 을 같은 "없음"으로 읽어야 한다.
	 *
	 * 주의. 토큰 원문도, 그 해시도 싣지 않는다. 이 메시지는 DB 밖(Kafka · 감사 저장소)으로 나가므로, 거기서
	 *      쓸 수 있는 토큰이나 그 행을 찾는 열쇠(token_hash)가 나가면 안 된다.
	 *
	 * 주의. 필드 이름과 kind 값의 철자는 토픽(v1)의 계약이다. 읽는 쪽이 이 철자로 필드를 찾으므로 바꾸려면
	 *      토픽 버전부터 올려야 한다.
	 */

	public static final String ISSUED = "ISSUED";
	public static final String ROTATED = "ROTATED";
	public static final String REVOKED = "REVOKED";
}
