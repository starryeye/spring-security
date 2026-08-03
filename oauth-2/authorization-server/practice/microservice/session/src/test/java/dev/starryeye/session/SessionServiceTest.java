package dev.starryeye.session;

import dev.starryeye.session.jpa.OidcSessionEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SessionServiceTest {

	@Autowired SessionService service;
	@Autowired OidcSessionEntityRepository repository;

	@BeforeEach
	void clean() {
		repository.deleteAll();
	}

	@Test
	void registersOneRowPerClient() {
		service.register("SID-1", "user-sub-0001", "demo-rp");
		service.register("SID-1", "user-sub-0001", "other-rp");

		assertThat(repository.findBySid("SID-1")).hasSize(2);
	}

	// 같은 RP 가 여러 번 code 를 교환할 수 있다. 그때마다 행이 늘면 로그아웃 때 같은 RP 로 여러 번 보내게 된다.
	@Test
	void registerIsIdempotentForTheSameClient() {
		service.register("SID-1", "user-sub-0001", "demo-rp");
		service.register("SID-1", "user-sub-0001", "demo-rp");

		assertThat(repository.findBySid("SID-1")).hasSize(1);
	}

	@Test
	void consumeForLogoutReturnsEveryClientOfThatSession() {
		service.register("SID-1", "user-sub-0001", "demo-rp");
		service.register("SID-1", "user-sub-0001", "other-rp");

		LogoutTargets targets = service.consumeForLogout("SID-1");

		assertThat(targets.sub()).isEqualTo("user-sub-0001");
		assertThat(targets.clientIds()).containsExactlyInAnyOrder("demo-rp", "other-rp");
	}

	// 세션은 로그아웃 시점에 끝난다. 발송 성공 여부와 무관하게 행을 지운다 —
	// 남겨두면 다음 로그아웃에서 이미 끝난 세션으로 다시 보낸다.
	@Test
	void consumeForLogoutDeletesTheRows() {
		service.register("SID-1", "user-sub-0001", "demo-rp");

		service.consumeForLogout("SID-1");

		assertThat(repository.findBySid("SID-1")).isEmpty();
	}

	@Test
	void consumeForLogoutDoesNotTouchOtherSessions() {
		service.register("SID-1", "user-sub-0001", "demo-rp");
		service.register("SID-2", "user-sub-0002", "demo-rp");

		service.consumeForLogout("SID-1");

		assertThat(repository.findBySid("SID-2")).hasSize(1);
	}

	@Test
	void unknownSessionYieldsEmptyTargets() {
		LogoutTargets targets = service.consumeForLogout("SID-NONE");

		assertThat(targets.sub()).isNull();
		assertThat(targets.clientIds()).isEmpty();
	}
}
