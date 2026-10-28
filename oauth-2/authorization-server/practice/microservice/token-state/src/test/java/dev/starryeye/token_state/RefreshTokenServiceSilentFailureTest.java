package dev.starryeye.token_state;

import dev.starryeye.token_state.jpa.OutboxEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RefreshTokenServiceSilentFailureTest {

	/**
	 * 상태를 바꾸지 않은 시도는 outbox 에 아무것도 남기지 않는다. 감사 이력에 "일어나지 않은 일"이 사건으로
	 *      남으면 이력이 사실과 달라진다 — 거절된 회전이 ROTATED 로, 아무것도 안 바뀐 폐기가 REVOKED 로 보이면 안 된다.
	 *
	 * 주의. 각 테스트는 시도 직전의 outbox 행 수를 재고 시도 뒤에도 같은지 본다. 이 모듈의 h2 는 다른 컨텍스트와
	 *      테이블을 공유하므로(OutboxEvents 주석 참고) 계열이 있는 경로는 그 계열(familyId)의 행 수만 세고,
	 *      계열이 없는 경로(없는 토큰)는 전체 행 수를 센다. 전체 행 수는 그 사이 다른 테스트가 outbox 에 쓰지
	 *      않는다는 전제에서만 쓸 수 있다 — 이 모듈은 테스트 병렬 실행을 설정하지 않는다.
	 */

	@Autowired
	private RefreshTokenService service;

	@Autowired
	private RefreshTokenEntityRepository repository;

	@Autowired
	private OutboxEntityRepository outboxRepository;

	@Autowired
	private TokenGenerator tokenGenerator;

	@Test
	@DisplayName("없는 토큰의 회전은 아무것도 기록하지 않는다")
	void notFoundRecordsNothing() {
		long before = outboxRepository.count();

		RotateResult result = service.rotate("no-such-token", "my-client", null);

		assertThat(result.status()).isEqualTo(RotateStatus.NOT_FOUND);
		assertThat(outboxRepository.count()).isEqualTo(before);
	}

	@Test
	@DisplayName("다른 client 의 회전은 아무것도 기록하지 않는다")
	void clientMismatchRecordsNothing() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1000L, "SID-A");
		int before = OutboxEvents.rowsOf(outboxRepository, issued.familyId()).size();

		RotateResult result = service.rotate(issued.refreshToken(), "other-client", null);

		assertThat(result.status()).isEqualTo(RotateStatus.CLIENT_MISMATCH);
		assertThat(OutboxEvents.rowsOf(outboxRepository, issued.familyId())).hasSize(before);
	}

	@Test
	@DisplayName("저장된 grant 를 벗어난 scope 의 회전은 아무것도 기록하지 않는다")
	void scopeExceededRecordsNothing() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1000L, "SID-A");
		int before = OutboxEvents.rowsOf(outboxRepository, issued.familyId()).size();

		RotateResult result = service.rotate(issued.refreshToken(), "my-client", "openid email");

		assertThat(result.status()).isEqualTo(RotateStatus.SCOPE_EXCEEDED);
		assertThat(OutboxEvents.rowsOf(outboxRepository, issued.familyId())).hasSize(before);
	}

	@Test
	@DisplayName("만료된 토큰의 회전은 아무것도 기록하지 않는다")
	void expiredRecordsNothing() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1000L, "SID-A");
		RefreshTokenEntity entity = repository.findByTokenHash(tokenGenerator.hash(issued.refreshToken())).orElseThrow();
		RefreshTokenFixtures.expireIndividualToken(repository, entity);
		int before = OutboxEvents.rowsOf(outboxRepository, issued.familyId()).size();

		RotateResult result = service.rotate(issued.refreshToken(), "my-client", null);

		assertThat(result.status()).isEqualTo(RotateStatus.EXPIRED);
		assertThat(OutboxEvents.rowsOf(outboxRepository, issued.familyId())).hasSize(before);
	}

	@Test
	@DisplayName("이미 폐기된 계열의 회전 재시도는 아무것도 기록하지 않는다")
	void retryWithRevokedTokenRecordsNothing() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1000L, "SID-A");
		RotateResult rotated = service.rotate(issued.refreshToken(), "my-client", null);
		service.rotate(issued.refreshToken(), "my-client", null);                 // 재사용 탐지로 계열이 폐기된다
		int before = OutboxEvents.rowsOf(outboxRepository, issued.familyId()).size();

		RotateResult result = service.rotate(rotated.refreshToken(), "my-client", null);

		assertThat(result.status()).isEqualTo(RotateStatus.REVOKED);
		assertThat(OutboxEvents.rowsOf(outboxRepository, issued.familyId())).hasSize(before);
	}

	@Test
	@DisplayName("없는 토큰의 폐기는 아무것도 기록하지 않는다")
	void revokeOfUnknownTokenRecordsNothing() {
		long before = outboxRepository.count();

		boolean revoked = service.revoke("no-such-token", "my-client");

		assertThat(revoked).isFalse();
		assertThat(outboxRepository.count()).isEqualTo(before);
	}

	@Test
	@DisplayName("다른 client 의 폐기는 아무것도 기록하지 않는다")
	void revokeByAnotherClientRecordsNothing() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1000L, "SID-A");
		int before = OutboxEvents.rowsOf(outboxRepository, issued.familyId()).size();

		boolean revoked = service.revoke(issued.refreshToken(), "other-client");

		assertThat(revoked).isFalse();
		assertThat(OutboxEvents.rowsOf(outboxRepository, issued.familyId())).hasSize(before);
	}

	@Test
	@DisplayName("이미 전부 끊긴 계열의 폐기는 true 를 돌려주되 아무것도 기록하지 않는다")
	void revokeOfFullyRevokedFamilyRecordsNothing() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid", 1000L, "SID-A");
		assertThat(service.revoke(issued.refreshToken(), "my-client")).isTrue();
		int before = OutboxEvents.rowsOf(outboxRepository, issued.familyId()).size();

		boolean revoked = service.revoke(issued.refreshToken(), "my-client");

		assertThat(revoked).isTrue();                                             // RFC 7009 응답은 그대로다
		assertThat(OutboxEvents.rowsOf(outboxRepository, issued.familyId())).hasSize(before);
	}
}
