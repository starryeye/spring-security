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
class RefreshTokenServiceRotateTest {

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

	@Test
	void rotateIssuesNewTokenInSameFamilyAndConsumesOld() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1700000000L);

		RotateResult result = service.rotate(issued.refreshToken(), "my-client");

		assertThat(result.status()).isEqualTo(RotateStatus.ROTATED);
		assertThat(result.refreshToken()).isNotEqualTo(issued.refreshToken());
		assertThat(result.sub()).isEqualTo("user-sub-0001");
		assertThat(result.scope()).isEqualTo("openid offline_access");
		assertThat(result.authTime()).isEqualTo(1700000000L);

		RefreshTokenEntity old = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken())).orElseThrow();
		assertThat(old.getStatus()).isEqualTo(RefreshTokenStatus.CONSUMED);
		assertThat(old.getConsumedAt()).isNotNull();

		RefreshTokenEntity fresh = repository.findByTokenHash(tokenGenerator.hash(result.refreshToken())).orElseThrow();
		assertThat(fresh.getFamilyId()).isEqualTo(old.getFamilyId());
		assertThat(fresh.getFamilyExpiresAt()).isEqualTo(old.getFamilyExpiresAt()); // 절대 상한은 복사, 연장되지 않는다
	}

	// 이 슬라이스의 핵심 보안 동작. 응답 status 만 보지 말고 DB 상태로 계열 전체를 확인한다.
	@Test
	void reusingConsumedTokenRevokesEntireFamily() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);
		RotateResult first = service.rotate(issued.refreshToken(), "my-client");

		RotateResult reuse = service.rotate(issued.refreshToken(), "my-client"); // 이미 소진된 토큰

		assertThat(reuse.status()).isEqualTo(RotateStatus.REUSE_DETECTED);
		assertThat(reuse.refreshToken()).isNull();

		String familyId = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken()))
				.orElseThrow().getFamilyId();
		List<RefreshTokenEntity> family = repository.findByFamilyId(familyId);
		assertThat(family).hasSize(2);
		assertThat(family).allMatch(e -> e.getStatus() == RefreshTokenStatus.REVOKED);
		assertThat(family).allMatch(e -> "REUSE_DETECTED".equals(e.getRevokedReason()));

		// 계열이 죽었으므로 방금 발급된 정상 토큰도 더는 쓸 수 없다
		RotateResult afterRevoke = service.rotate(first.refreshToken(), "my-client");
		assertThat(afterRevoke.status()).isEqualTo(RotateStatus.REVOKED);
	}

	@Test
	void rotateWithUnknownTokenReturnsNotFound() {
		RotateResult result = service.rotate("no-such-token", "my-client");

		assertThat(result.status()).isEqualTo(RotateStatus.NOT_FOUND);
	}

	// client 가 다르면 상태를 바꾸지 않는다. 남의 토큰을 제출해 계열을 죽이는 공격을 막는다.
	@Test
	void rotateWithMismatchedClientChangesNothing() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);

		RotateResult result = service.rotate(issued.refreshToken(), "other-client");

		assertThat(result.status()).isEqualTo(RotateStatus.CLIENT_MISMATCH);
		RefreshTokenEntity entity = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken())).orElseThrow();
		assertThat(entity.getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
	}

	// 개별 토큰은 아직 유효하지만 계열 절대 상한을 넘긴 경우를 격리해 검증한다.
	// (테스트 설정: ttl 60초, family 최대 300초)
	@Test
	void rotateAfterFamilyAbsoluteExpiryReturnsExpired() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);
		RefreshTokenEntity entity = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken())).orElseThrow();
		// 계열 상한만 과거로 옮긴다. expires_at 은 그대로 미래다.
		repository.save(expireFamily(entity));

		RotateResult result = service.rotate(issued.refreshToken(), "my-client");

		assertThat(result.status()).isEqualTo(RotateStatus.EXPIRED);
	}

	private RefreshTokenEntity expireFamily(RefreshTokenEntity entity) {
		RefreshTokenEntity replaced = RefreshTokenEntity.builder()
				.tokenHash(entity.getTokenHash())
				.familyId(entity.getFamilyId())
				.clientId(entity.getClientId())
				.sub(entity.getSub())
				.scopes(entity.getScopes())
				.authTime(entity.getAuthTime())
				.issuedAt(entity.getIssuedAt())
				.expiresAt(entity.getExpiresAt())
				.familyExpiresAt(entity.getIssuedAt().minusSeconds(1))
				.build();
		repository.delete(entity);
		repository.flush();
		return replaced;
	}
}
