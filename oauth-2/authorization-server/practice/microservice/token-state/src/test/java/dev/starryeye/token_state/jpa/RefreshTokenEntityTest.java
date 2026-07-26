package dev.starryeye.token_state.jpa;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenEntityTest {

	private RefreshTokenEntity newEntity() {
		Instant now = Instant.parse("2026-07-25T00:00:00Z");
		return RefreshTokenEntity.builder()
				.tokenHash("hash-1")
				.familyId("family-1")
				.clientId("my-client")
				.sub("user-sub-0001")
				.scopes("openid,offline_access")
				.authTime(1700000000L)
				.issuedAt(now)
				.expiresAt(now.plusSeconds(60))
				.familyExpiresAt(now.plusSeconds(600))
				.build();
	}

	@Test
	void newEntityIsActive() {
		assertThat(newEntity().getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
	}

	@Test
	void consumeMarksConsumedAndRecordsTime() {
		RefreshTokenEntity entity = newEntity();
		Instant at = Instant.parse("2026-07-25T00:00:30Z");

		entity.consume(at);

		assertThat(entity.getStatus()).isEqualTo(RefreshTokenStatus.CONSUMED);
		assertThat(entity.getConsumedAt()).isEqualTo(at);
	}

	@Test
	void revokeMarksRevokedWithReason() {
		RefreshTokenEntity entity = newEntity();
		Instant at = Instant.parse("2026-07-25T00:00:30Z");

		entity.revoke(at, "REUSE_DETECTED");

		assertThat(entity.getStatus()).isEqualTo(RefreshTokenStatus.REVOKED);
		assertThat(entity.getRevokedAt()).isEqualTo(at);
		assertThat(entity.getRevokedReason()).isEqualTo("REUSE_DETECTED");
	}

	// 이미 소진된 토큰도 폐기 대상이다. 계열 폐기는 CONSUMED 행까지 REVOKED 로 바꿔
	// "이 계열은 끝났다"를 한 가지 상태로 표현한다.
	@Test
	void consumedEntityCanStillBeRevoked() {
		RefreshTokenEntity entity = newEntity();
		entity.consume(Instant.parse("2026-07-25T00:00:30Z"));

		entity.revoke(Instant.parse("2026-07-25T00:01:00Z"), "REUSE_DETECTED");

		assertThat(entity.getStatus()).isEqualTo(RefreshTokenStatus.REVOKED);
	}
}
