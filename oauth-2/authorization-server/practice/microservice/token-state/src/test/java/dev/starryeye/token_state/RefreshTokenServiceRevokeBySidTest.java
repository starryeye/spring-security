package dev.starryeye.token_state;

import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RefreshTokenServiceRevokeBySidTest {

	/**
	 * 로그아웃은 그 세션에 속한 refresh token 만 죽인다. 같은 사용자가 다른 브라우저에서 만든 세션은 살아야 한다.
	 *
	 * 주의. 조건부 갱신(status = ACTIVE)이라 두 번 실행해도 결과가 같다. Kafka 가 at-least-once 이므로
	 *      같은 이벤트를 두 번 받는 일이 실제로 일어나는데, 이 성질 덕에 별도 dedupe 표가 필요 없다.
	 */

	@Autowired
	private RefreshTokenService service;

	@Autowired
	private RefreshTokenEntityRepository repository;

	/**
	 * @SpringBootTest 는 클래스 사이에 컨텍스트(와 H2 인메모리 DB)를 재사용하고, 이 클래스 안에서도 메서드마다
	 *      트랜잭션을 되돌리지 않는다. unknownSidChangesNothing 은 정의상 ACTIVE 인 sid=SID-A 행을 하나
	 *      남기고 끝나므로, JUnit5 의 기본 메서드 순서(소스 순서가 아니다)가 그 뒤에 revokesOnlyThatSession 을
	 *      돌리면 남은 행이 개수 단언을 오염시킨다. 이 모듈의 다른 @SpringBootTest 클래스들
	 *      (RefreshTokenServiceRevokeTest 등)이 전부 쓰는 관례를 그대로 따른다.
	 */
	@BeforeEach
	void clean() {
		repository.deleteAll();
	}

	@Test
	@DisplayName("그 세션의 refresh 만 폐기하고 다른 세션은 건드리지 않는다")
	void revokesOnlyThatSession() {
		IssueResult sessionA = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");
		IssueResult sessionB = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-B");

		int revoked = service.revokeBySid("SID-A");

		assertThat(revoked).isEqualTo(1);
		assertThat(statusOf(sessionA)).isEqualTo(RefreshTokenStatus.REVOKED);
		assertThat(statusOf(sessionB)).isEqualTo(RefreshTokenStatus.ACTIVE);
	}

	@Test
	@DisplayName("한 세션에 여러 client 가 붙어 있으면 전부 폐기한다")
	void revokesEveryClientOfThatSession() {
		IssueResult first = service.issue("client-one", "user-sub-0001", "openid offline_access", 1000L, "SID-A");
		IssueResult second = service.issue("client-two", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

		int revoked = service.revokeBySid("SID-A");

		assertThat(revoked).isEqualTo(2);
		assertThat(statusOf(first)).isEqualTo(RefreshTokenStatus.REVOKED);
		assertThat(statusOf(second)).isEqualTo(RefreshTokenStatus.REVOKED);
	}

	@Test
	@DisplayName("두 번 폐기해도 결과가 같다 — 두 번째는 아무 행도 바꾸지 않는다")
	void isIdempotent() {
		service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

		assertThat(service.revokeBySid("SID-A")).isEqualTo(1);
		assertThat(service.revokeBySid("SID-A")).isEqualTo(0);
	}

	@Test
	@DisplayName("모르는 sid 는 아무것도 바꾸지 않는다")
	void unknownSidChangesNothing() {
		service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

		assertThat(service.revokeBySid("SID-UNKNOWN")).isEqualTo(0);
	}

	private RefreshTokenStatus statusOf(IssueResult issued) {
		return repository.findByFamilyId(issued.familyId()).get(0).getStatus();
	}
}
