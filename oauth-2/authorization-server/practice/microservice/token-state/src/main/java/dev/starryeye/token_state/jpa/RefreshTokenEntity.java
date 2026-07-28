package dev.starryeye.token_state.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_tokens_family", columnList = "family_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenEntity {

	/**
	 * refresh token 한 개를 나타낸다. 회전하면 기존 행은 CONSUMED 가 되고 같은 family_id 로 새 행이 생기므로,
	 *      한 계열(family)은 여러 행으로 남아 발급 이력이 그대로 감사 기록이 된다.
	 *
	 * 주의. 토큰 원문을 저장하지 않고 SHA-256 해시만 보관한다. DB 가 유출돼도 쓸 수 있는 토큰이 나오지 않는다.
	 *      salt 를 쓰지 않는 이유는 해시로 행을 찾아야 해서 조회가 결정적이어야 하기 때문이며,
	 *      원문이 256비트 난수라 사전 공격 대상이 아니어서 성립한다. 사용자 비밀번호에는 같은 논리를 적용할 수 없다.
	 *
	 * 주의. family_expires_at 은 회전 때 그대로 복사한다. expires_at 만 갱신되므로 회전을 반복해도
	 *      계열 자체의 수명은 늘어나지 않는다.
	 */

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "family_id", nullable = false, length = 36)
	private String familyId;

	@Column(name = "client_id", nullable = false)
	private String clientId;

	@Column(name = "sub", nullable = false)
	private String sub;

	@Column(nullable = false, length = 1000)
	private String scopes; // comma 구분

	@Column(name = "auth_time", nullable = false)
	private long authTime;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private RefreshTokenStatus status;

	@Column(name = "issued_at", nullable = false)
	private Instant issuedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "family_expires_at", nullable = false)
	private Instant familyExpiresAt;

	@Column(name = "consumed_at")
	private Instant consumedAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "revoked_reason", length = 30)
	private String revokedReason;

	@Builder
	private RefreshTokenEntity(String tokenHash, String familyId, String clientId, String sub, String scopes,
			long authTime, Instant issuedAt, Instant expiresAt, Instant familyExpiresAt) {
		this.tokenHash = tokenHash;
		this.familyId = familyId;
		this.clientId = clientId;
		this.sub = sub;
		this.scopes = scopes;
		this.authTime = authTime;
		this.issuedAt = issuedAt;
		this.expiresAt = expiresAt;
		this.familyExpiresAt = familyExpiresAt;
		this.status = RefreshTokenStatus.ACTIVE;
	}

	public void consume(Instant at) {
		this.status = RefreshTokenStatus.CONSUMED;
		this.consumedAt = at;
	}

	/**
	 * 이미 REVOKED 면 아무것도 바꾸지 않는다. 최초 폐기 사유가 감사 기록이므로,
	 *      나중에 온 폐기가 REUSE_DETECTED 를 CLIENT_REVOKED 로 덮어쓰면 탈취 탐지 흔적이 사라진다.
	 */
	public void revoke(Instant at, String reason) {
		if (this.status == RefreshTokenStatus.REVOKED) {
			return;
		}
		this.status = RefreshTokenStatus.REVOKED;
		this.revokedAt = at;
		this.revokedReason = reason;
	}
}
