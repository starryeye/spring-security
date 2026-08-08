package dev.starryeye.token_state;

import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RefreshTokenServiceSidTest {

	/**
	 * refresh token 레코드가 sid 를 보관해야 폐기 범위를 세션 단위로 잡을 수 있다. sub + client_id 로만
	 *      죽이면 다른 브라우저에서 로그인한 세션까지 함께 죽는다.
	 */

	@Autowired
	private RefreshTokenService service;

	@Autowired
	private RefreshTokenEntityRepository repository;

	@Test
	@DisplayName("발급하면 sid 가 저장된다")
	void issueStoresSid() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

		RefreshTokenEntity saved = repository.findByFamilyId(issued.familyId()).get(0);
		assertThat(saved.getSid()).isEqualTo("SID-A");
	}

	@Test
	@DisplayName("회전하면 새 행이 같은 sid 를 승계한다")
	void rotateInheritsSid() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

		RotateResult rotated = service.rotate(issued.refreshToken(), "my-client", null);
		assertThat(rotated.status()).isEqualTo(RotateStatus.ROTATED);

		List<RefreshTokenEntity> family = repository.findByFamilyId(issued.familyId());
		assertThat(family).hasSize(2);
		assertThat(family).allSatisfy(member -> assertThat(member.getSid()).isEqualTo("SID-A"));
	}

	@Test
	@DisplayName("회전 응답이 sid 를 알려준다 — refresh 로 재발급하는 id token 에 실어야 하기 때문이다")
	void rotateResultCarriesSid() {
		IssueResult issued = service.issue("my-client", "user-sub-0001", "openid offline_access", 1000L, "SID-A");

		RotateResult rotated = service.rotate(issued.refreshToken(), "my-client", null);

		assertThat(rotated.sid()).isEqualTo("SID-A");
	}

	@Test
	@DisplayName("sid 가 없어도 발급된다 — client_credentials 처럼 세션이 없는 경로가 있다")
	void issueAllowsNullSid() {
		IssueResult issued = service.issue("my-client", "user-sub-0002", "offline_access", 1000L, null);

		RefreshTokenEntity saved = repository.findByFamilyId(issued.familyId()).get(0);
		assertThat(saved.getSid()).isNull();
	}
}
