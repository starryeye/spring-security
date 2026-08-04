package dev.starryeye.session;

import dev.starryeye.session.jpa.OidcSessionEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class SessionServiceTest {

	@Autowired SessionService service;
	@MockitoSpyBean OidcSessionEntityRepository repository;

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

	// register 의 선검사(existsBySidAndClientId)만으로는 동시 호출을 막지 못한다. existsBySidAndClientId 에
	// 장벽을 세워 두 스레드가 나란히 "없음" 을 확인하게 강제한 뒤, 그래도 둘 다 예외 없이 끝나고 행이
	// 하나만 남는지 본다 — h2 가 uk_sid_client 를 강제한다는 전제와, register 가 그 위반을 흡수한다는
	// 전제를 한 번에 검증한다.
	@Test
	void concurrentRegisterForTheSameClientInsertsExactlyOneRowWithoutThrowing() throws Exception {
		CountDownLatch bothEnteredCheck = new CountDownLatch(2);
		doAnswer(invocation -> {
			// existsBySidAndClientId 는 Spring Data 가 만든 프록시 메서드라 callRealMethod() 를 쓸 수 없다.
			// @BeforeEach 로 테이블을 비워둔 상태이므로 실제 조회 결과와 같은 값(false)을 직접 돌려준다 —
			// 중요한 건 반환값이 아니라, 두 스레드가 이 지점에서 나란히 멈췄다가 함께 풀려나는 것이다.
			bothEnteredCheck.countDown();
			bothEnteredCheck.await(10, TimeUnit.SECONDS);
			return false;
		}).when(repository).existsBySidAndClientId("SID-1", "demo-rp");

		Callable<Void> register = () -> {
			service.register("SID-1", "user-sub-0001", "demo-rp");
			return null;
		};
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			List<Future<Void>> futures = pool.invokeAll(List.of(register, register));
			for (Future<Void> future : futures) {
				future.get(30, TimeUnit.SECONDS);
			}
		} finally {
			pool.shutdownNow();
		}

		assertThat(repository.findBySid("SID-1")).hasSize(1);
	}
}
