package dev.starryeye.token_state;

import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringBootTest
class RefreshTokenServiceIssueTest {

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
	void issueStoresHashNotRawToken() {
		IssueResult result = service.issue("my-client", "user-sub-0001", "openid offline_access", 1700000000L);

		assertThat(result.refreshToken()).isNotBlank();
		assertThat(repository.findByTokenHash(result.refreshToken())).isEmpty(); // 원문으로는 찾을 수 없다
		Optional<RefreshTokenEntity> found = repository.findByTokenHash(tokenGenerator.hash(result.refreshToken()));
		assertThat(found).isPresent();
		assertThat(found.get().getStatus()).isEqualTo(RefreshTokenStatus.ACTIVE);
	}

	// API 는 공백 구분, DB 는 comma 구분이다
	@Test
	void issueConvertsScopeToCommaForStorage() {
		IssueResult result = service.issue("my-client", "user-sub-0001", "openid offline_access", 1700000000L);

		RefreshTokenEntity entity = repository.findByTokenHash(tokenGenerator.hash(result.refreshToken())).orElseThrow();
		assertThat(entity.getScopes()).isEqualTo("openid,offline_access");
	}

	@Test
	void issueStartsNewFamilyEachTime() {
		IssueResult first = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);
		IssueResult second = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);

		assertThat(first.familyId()).isNotEqualTo(second.familyId());
	}

	@Test
	void issueSetsFamilyExpiryFromConfiguredMaximum() {
		IssueResult result = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);

		RefreshTokenEntity entity = repository.findByTokenHash(tokenGenerator.hash(result.refreshToken())).orElseThrow();
		// 테스트 설정: ttl 60초, family 최대 300초
		assertThat(entity.getFamilyExpiresAt()).isAfter(entity.getExpiresAt());
		// 설정값 자체를 issuedAt 기준으로 고정한다 (두 TTL 이 서로 바뀌어 주입돼도 잡아낸다)
		assertThat(entity.getExpiresAt())
				.isCloseTo(entity.getIssuedAt().plusSeconds(60), within(2, ChronoUnit.SECONDS));
		assertThat(entity.getFamilyExpiresAt())
				.isCloseTo(entity.getIssuedAt().plusSeconds(300), within(2, ChronoUnit.SECONDS));
	}

	@Test
	void introspectReturnsActiveWithClaims() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1700000000L);

		IntrospectResult result = service.introspect(issued.refreshToken());

		assertThat(result.active()).isTrue();
		assertThat(result.sub()).isEqualTo("user-sub-0001");
		assertThat(result.clientId()).isEqualTo("my-client");
		assertThat(result.scope()).isEqualTo("openid offline_access"); // 응답은 공백 구분으로 되돌린다
	}

	@Test
	void introspectReturnsInactiveForUnknownToken() {
		IntrospectResult result = service.introspect("no-such-token");

		assertThat(result.active()).isFalse();
		assertThat(result.sub()).isNull();
		assertThat(result.clientId()).isNull();
		assertThat(result.scope()).isNull();
	}

	// 개별 만료만 격리해 검증한다. family_expires_at 은 미래로 두고 expires_at 만 과거로 옮긴다.
	// RefreshTokenServiceRotateTest 의 rotateWithExpiredIndividualTokenButFamilyStillValidReturnsExpired 와 같은 관용구다.
	@Test
	void introspectReturnsInactiveWhenIndividualTokenExpiredButFamilyStillValid() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1700000000L);
		RefreshTokenEntity entity = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken())).orElseThrow();
		repository.save(expireIndividualToken(entity));

		IntrospectResult result = service.introspect(issued.refreshToken());

		assertThat(result.active()).isFalse();
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
				.build();
		repository.delete(entity);
		repository.flush();
		return replaced;
	}
}
