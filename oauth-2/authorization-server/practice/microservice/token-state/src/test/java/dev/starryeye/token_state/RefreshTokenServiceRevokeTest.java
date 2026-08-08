package dev.starryeye.token_state;

import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RefreshTokenServiceRevokeTest {

	@Autowired
	private RefreshTokenService service;

	@Autowired
	private RefreshTokenEntityRepository repository;

	@Autowired
	private TokenGenerator tokenGenerator;

	@BeforeEach
	void clean() {
		repository.deleteAll();
	}

	// refresh token 하나는 하나의 grant 를 대표하므로, 폐기는 그 grant 를 끝내는 것이다 (RFC 7009 2.1)
	@Test
	void revokeKillsEntireFamily() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L, null);
		RotateResult rotated = service.rotate(issued.refreshToken(), "my-client", null);

		boolean revoked = service.revoke(rotated.refreshToken(), "my-client");

		assertThat(revoked).isTrue();
		String familyId = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken()))
				.orElseThrow().getFamilyId();
		List<RefreshTokenEntity> family = repository.findByFamilyId(familyId);
		assertThat(family).hasSize(2);
		assertThat(family).allMatch(e -> e.getStatus() == RefreshTokenStatus.REVOKED);
		assertThat(family).allMatch(e -> "CLIENT_REVOKED".equals(e.getRevokedReason()));
	}

	@Test
	void revokeAfterRevokeStillRotatesToRevoked() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L, null);
		service.revoke(issued.refreshToken(), "my-client");

		RotateResult result = service.rotate(issued.refreshToken(), "my-client", null);

		assertThat(result.status()).isEqualTo(RotateStatus.REVOKED);
	}

	@Test
	void revokeUnknownTokenReturnsFalse() {
		assertThat(service.revoke("no-such-token", "my-client")).isFalse();
	}

	// 남의 토큰으로는 아무것도 폐기할 수 없다
	@Test
	void revokeWithMismatchedClientChangesNothing() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L, null);

		boolean revoked = service.revoke(issued.refreshToken(), "other-client");

		assertThat(revoked).isFalse();
		RefreshTokenEntity entity = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken())).orElseThrow();
		assertThat(entity.getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
	}

	// 재사용 탐지로 폐기된 계열에 client 폐기가 뒤따라도 최초 사유가 남아야 한다.
	@Test
	void revokeDoesNotOverwriteReuseDetectedReason() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L, null);
		RotateResult rotated = service.rotate(issued.refreshToken(), "my-client", null);
		service.rotate(issued.refreshToken(), "my-client", null); // 재사용 -> 계열 REUSE_DETECTED

		service.revoke(rotated.refreshToken(), "my-client");

		String familyId = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken()))
				.orElseThrow().getFamilyId();
		// 계열은 최초 발급분 1행 + 첫 회전으로 생긴 1행, 총 2행이다. 재사용 탐지는 새 행을 만들지 않고
		// 기존 행들만 REVOKED 로 전이한다. hasSize 없이 allMatch 만 쓰면 빈 컬렉션에서도 공허하게 통과하므로
		// 행 수를 먼저 고정한다.
		assertThat(repository.findByFamilyId(familyId)).hasSize(2);
		assertThat(repository.findByFamilyId(familyId))
				.allMatch(e -> "REUSE_DETECTED".equals(e.getRevokedReason()));
	}
}
