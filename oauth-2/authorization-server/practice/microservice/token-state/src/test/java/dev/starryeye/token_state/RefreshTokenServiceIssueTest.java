package dev.starryeye.token_state;

import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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
}
