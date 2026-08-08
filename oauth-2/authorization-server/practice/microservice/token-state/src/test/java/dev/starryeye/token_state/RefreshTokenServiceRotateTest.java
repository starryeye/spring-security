package dev.starryeye.token_state;

import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
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
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1700000000L, null);

		RotateResult result = service.rotate(issued.refreshToken(), "my-client", null);

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
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L, null);
		RotateResult first = service.rotate(issued.refreshToken(), "my-client", null);

		RotateResult reuse = service.rotate(issued.refreshToken(), "my-client", null); // 이미 소진된 토큰

		assertThat(reuse.status()).isEqualTo(RotateStatus.REUSE_DETECTED);
		assertThat(reuse.refreshToken()).isNull();

		String familyId = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken()))
				.orElseThrow().getFamilyId();
		List<RefreshTokenEntity> family = repository.findByFamilyId(familyId);
		assertThat(family).hasSize(2);
		assertThat(family).allMatch(e -> e.getStatus() == RefreshTokenStatus.REVOKED);
		assertThat(family).allMatch(e -> "REUSE_DETECTED".equals(e.getRevokedReason()));

		// 계열이 죽었으므로 방금 발급된 정상 토큰도 더는 쓸 수 없다
		RotateResult afterRevoke = service.rotate(first.refreshToken(), "my-client", null);
		assertThat(afterRevoke.status()).isEqualTo(RotateStatus.REVOKED);
	}

	// 계열에 3행 이상 쌓인 뒤 재사용해도, 폐기는 그 순간 계열에 실제로 존재하는 행 전부를 대상으로 해야 한다.
	// 폐기 판정을 오래된 스냅샷이 아니라 잠근 뒤 다시 읽은 최신 목록으로 하는지를 단일 스레드로 고정한다.
	@Test
	void reusingConsumedTokenAfterMultipleRotationsRevokesEveryFamilyMember() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L, null);
		RotateResult first = service.rotate(issued.refreshToken(), "my-client", null);
		RotateResult second = service.rotate(first.refreshToken(), "my-client", null);
		assertThat(second.status()).isEqualTo(RotateStatus.ROTATED); // 계열은 이제 3행: 최초, first, second

		RotateResult reuse = service.rotate(issued.refreshToken(), "my-client", null); // 최초 토큰을 다시 제출

		assertThat(reuse.status()).isEqualTo(RotateStatus.REUSE_DETECTED);
		String familyId = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken()))
				.orElseThrow().getFamilyId();
		List<RefreshTokenEntity> family = repository.findByFamilyId(familyId);
		assertThat(family).hasSize(3);
		assertThat(family).allMatch(e -> e.getStatus() == RefreshTokenStatus.REVOKED);
		assertThat(family).allMatch(e -> "REUSE_DETECTED".equals(e.getRevokedReason()));
	}

	@Test
	void rotateWithUnknownTokenReturnsNotFound() {
		RotateResult result = service.rotate("no-such-token", "my-client", null);

		assertThat(result.status()).isEqualTo(RotateStatus.NOT_FOUND);
	}

	// client 가 다르면 상태를 바꾸지 않는다. 남의 토큰을 제출해 계열을 죽이는 공격을 막는다.
	@Test
	void rotateWithMismatchedClientChangesNothing() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L, null);

		RotateResult result = service.rotate(issued.refreshToken(), "other-client", null);

		assertThat(result.status()).isEqualTo(RotateStatus.CLIENT_MISMATCH);
		RefreshTokenEntity entity = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken())).orElseThrow();
		assertThat(entity.getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
	}

	// 개별 토큰은 아직 유효하지만 계열 절대 상한을 넘긴 경우를 격리해 검증한다.
	// (테스트 설정: ttl 60초, family 최대 300초)
	@Test
	void rotateAfterFamilyAbsoluteExpiryReturnsExpired() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L, null);
		RefreshTokenEntity entity = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken())).orElseThrow();
		// 계열 상한만 과거로 옮긴다. expires_at 은 그대로 미래다.
		repository.save(expireFamily(entity));

		RotateResult result = service.rotate(issued.refreshToken(), "my-client", null);

		assertThat(result.status()).isEqualTo(RotateStatus.EXPIRED);
	}

	// 개별 만료만 격리해 검증한다. family_expires_at 은 미래로 두고 expires_at 만 과거로 옮긴다.
	// isExpired 의 두 항(개별/계열) 중 개별 항이 실제로 판정에 쓰이는지 고정한다 — 계열 상한 케이스와
	// 반대 격리다. (테스트 설정: ttl 60초, family 최대 300초)
	@Test
	void rotateWithExpiredIndividualTokenButFamilyStillValidReturnsExpired() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L, null);
		RefreshTokenEntity entity = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken())).orElseThrow();
		// 개별 만료만 과거로 옮긴다. family_expires_at 은 그대로 미래다.
		repository.save(expireIndividualToken(entity));

		RotateResult result = service.rotate(issued.refreshToken(), "my-client", null);

		assertThat(result.status()).isEqualTo(RotateStatus.EXPIRED);
	}

	// 축소 요청이 저장된 grant 를 벗어나면 아무 상태도 바꾸지 않고 거절한다.
	// 상태를 바꾼 뒤 거절하면 새 토큰 원문이 버려지고, client 가 이전 토큰으로 재시도하는 순간 계열이 죽는다.
	@Test
	void rotateWithScopeBeyondStoredGrantChangesNothing() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1700000000L, null);

		RotateResult result = service.rotate(issued.refreshToken(), "my-client", "openid admin");

		assertThat(result.status()).isEqualTo(RotateStatus.SCOPE_EXCEEDED);
		assertThat(result.refreshToken()).isNull();
		RefreshTokenEntity entity = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken())).orElseThrow();
		assertThat(entity.getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
		assertThat(repository.findByFamilyId(entity.getFamilyId())).hasSize(1); // 새 행이 생기지 않았다

		// 거절당한 뒤에도 같은 토큰을 그대로 다시 쓸 수 있다
		assertThat(service.rotate(issued.refreshToken(), "my-client", null).status())
				.isEqualTo(RotateStatus.ROTATED);
	}

	// 재사용 탐지가 scope 검사보다 먼저다. 잘못된 scope 를 함께 보내는 것으로 탐지를 건너뛸 수 없다.
	@Test
	void reuseIsDetectedEvenWhenRequestedScopeAlsoExceedsTheGrant() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L, null);
		service.rotate(issued.refreshToken(), "my-client", null);

		RotateResult reuse = service.rotate(issued.refreshToken(), "my-client", "openid admin");

		assertThat(reuse.status()).isEqualTo(RotateStatus.REUSE_DETECTED);
	}

	// 축소는 이번 요청에만 적용되고 저장 scope 는 불변이다. 아니면 한 번의 축소가 영구화된다.
	@Test
	void narrowedScopeRotatesButStoredScopeStaysIntact() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid profile offline_access", 1700000000L, null);

		RotateResult result = service.rotate(issued.refreshToken(), "my-client", "profile");

		assertThat(result.status()).isEqualTo(RotateStatus.ROTATED);
		assertThat(result.scope()).isEqualTo("openid profile offline_access"); // 응답은 저장된 grant 그대로다
		RefreshTokenEntity fresh = repository.findByTokenHash(tokenGenerator.hash(result.refreshToken())).orElseThrow();
		assertThat(fresh.getScopes()).isEqualTo("openid,profile,offline_access");
	}

	// 계열 상한이 개별 TTL 보다 가까우면 새 행의 수명은 상한에서 끊긴다.
	// 그러지 않으면 응답의 expiresAt 이 실제보다 긴 수명을 알린다.
	@Test
	void rotatedTokenExpiryNeverExceedsFamilyCeiling() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L, null);
		RefreshTokenEntity entity = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken())).orElseThrow();
		Instant nearCeiling = Instant.now().plusSeconds(5);
		repository.save(withFamilyExpiresAt(entity, nearCeiling));

		RotateResult result = service.rotate(issued.refreshToken(), "my-client", null);

		assertThat(result.status()).isEqualTo(RotateStatus.ROTATED);
		RefreshTokenEntity rotated = repository.findByTokenHash(tokenGenerator.hash(result.refreshToken())).orElseThrow();
		assertThat(rotated.getExpiresAt()).isEqualTo(rotated.getFamilyExpiresAt());
		assertThat(result.expiresAt()).isEqualTo(nearCeiling.getEpochSecond());
	}

	private RefreshTokenEntity withFamilyExpiresAt(RefreshTokenEntity entity, Instant familyExpiresAt) {
		RefreshTokenEntity replaced = RefreshTokenEntity.builder()
				.tokenHash(entity.getTokenHash())
				.familyId(entity.getFamilyId())
				.clientId(entity.getClientId())
				.sub(entity.getSub())
				.scopes(entity.getScopes())
				.authTime(entity.getAuthTime())
				.issuedAt(entity.getIssuedAt())
				.expiresAt(entity.getExpiresAt())
				.familyExpiresAt(familyExpiresAt)
				.sid(entity.getSid())
				.build();
		repository.delete(entity);
		repository.flush();
		return replaced;
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
				.sid(entity.getSid())
				.build();
		repository.delete(entity);
		repository.flush();
		return replaced;
	}

	private RefreshTokenEntity expireIndividualToken(RefreshTokenEntity entity) {
		RefreshTokenEntity replaced = RefreshTokenEntity.builder()
				.tokenHash(entity.getTokenHash())
				.familyId(entity.getFamilyId())
				.clientId(entity.getClientId())
				.sub(entity.getSub())
				.scopes(entity.getScopes())
				.authTime(entity.getAuthTime())
				.issuedAt(entity.getIssuedAt())
				.expiresAt(entity.getIssuedAt().minusSeconds(1))
				.familyExpiresAt(entity.getFamilyExpiresAt())
				.sid(entity.getSid())
				.build();
		repository.delete(entity);
		repository.flush();
		return replaced;
	}
}
