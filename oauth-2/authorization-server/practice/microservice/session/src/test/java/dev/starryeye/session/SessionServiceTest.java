package dev.starryeye.session;

import dev.starryeye.session.event.LogoutEventPublisher;
import dev.starryeye.session.jpa.OidcSessionEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

@SpringBootTest
class SessionServiceTest {

	// 주의. LogoutEventPublisher 를 MockitoBean 으로 바꿔 실제 Kafka 발행을 끊는다. 이 클래스는 세션
	//      레지스트리(register/consumeForLogout 의 조회·삭제) 동작을 보는 순수 단위 테스트였는데, Task 5 에서
	//      consumeForLogout 이 발행을 블로킹으로 기다리게 되면서 Kafka 브로커가 없으면 producer 의
	//      max.block.ms(기본 60초) 만큼 멈췄다가 실패하는 외부 인프라 의존이 처음 생겼다. 발행 자체(페이로드
	//      내용·파티션 키)는 LogoutEventPublishTest 가 EmbeddedKafka 로 이미 검증하므로, 여기서는 스텁으로
	//      끊어도 잃는 커버리지가 없다.
	@Autowired SessionService service;
	@MockitoSpyBean OidcSessionEntityRepository repository;
	@MockitoBean LogoutEventPublisher logoutEventPublisher;

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

		assertThat(targets.targets()).containsExactlyInAnyOrder(
				new LogoutTargets.Target("demo-rp", "user-sub-0001"),
				new LogoutTargets.Target("other-rp", "user-sub-0001"));
	}

	// 하나의 sid 아래 서로 다른 사용자의 행이 공존할 수 있다(재로그인이 sid 를 재사용한 적이 있었다면).
	// 대표값 하나를 모든 RP 에 재사용하면 남의 sub 가 새어나간다 — 각 RP 는 반드시 자기 행의 sub 를 받아야 한다.
	@Test
	void consumeForLogoutPairsEachClientWithItsOwnSub() {
		service.register("SID-1", "user-sub-A", "rp1");
		service.register("SID-1", "user-sub-B", "rp2");

		LogoutTargets targets = service.consumeForLogout("SID-1");

		assertThat(targets.targets()).containsExactlyInAnyOrder(
				new LogoutTargets.Target("rp1", "user-sub-A"),
				new LogoutTargets.Target("rp2", "user-sub-B"));
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

		assertThat(targets.targets()).isEmpty();
	}

	// register 의 선검사(existsBySidAndClientId)만으로는 동시 호출을 막지 못한다. existsBySidAndClientId 에
	// 장벽을 세워 두 스레드가 나란히 "없음" 을 확인하게 강제한 뒤, 그래도 둘 다 예외 없이 끝나고 행이
	// 하나만 남는지 본다 — h2 가 uk_sid_client 를 강제한다는 전제와, register 가 그 위반을 흡수한다는
	// 전제를 한 번에 검증한다.
	@Test
	void concurrentRegisterForTheSameClientInsertsExactlyOneRowWithoutThrowing() throws Exception {
		CountDownLatch bothEnteredCheck = new CountDownLatch(2);
		// existsBySidAndClientId 는 Spring Data 가 만든 프록시 메서드라 callRealMethod() 를 쓸 수 없다.
		// @BeforeEach 로 테이블을 비워둔 상태이므로 실제 조회 결과와 같은 값(false)을 직접 돌려준다 —
		// 중요한 건 반환값이 아니라, 두 스레드가 이 지점에서 나란히 멈췄다가 함께 풀려나는 것이다.
		//
		// register 는 이 메서드를 최대 두 번 부른다: 선검사, 그리고(A-3) save() 가 유니크 위반으로 실패했을 때의
		// 재확인. 재확인 호출은 인과적으로 두 스레드의 선검사보다 항상 뒤에 온다 — 진 쪽이 save() 에서 예외를
		// 받으려면 두 스레드 모두 먼저 이 지점을 통과해 있어야 하기 때문이다. 그래서 처음 두 번의 호출에는
		// 래치로 두 스레드를 동시에 세워 실제 경합을 만들고, 그 이후 호출(진 쪽의 재확인)에는 이미 이긴
		// 스레드가 저장을 마친 뒤이므로 실제 사실(true)을 그대로 돌려준다.
		doAnswer(invocation -> {
			bothEnteredCheck.countDown();
			bothEnteredCheck.await(10, TimeUnit.SECONDS);
			return false;
		}).doAnswer(invocation -> {
			bothEnteredCheck.countDown();
			bothEnteredCheck.await(10, TimeUnit.SECONDS);
			return false;
		}).doReturn(true)
				.when(repository).existsBySidAndClientId("SID-1", "demo-rp");

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

	// DataIntegrityViolationException 은 유니크 위반만이 아니라 길이 초과 등 다른 제약 위반에서도 난다.
	// clientId 는 컬럼 길이가 100 이므로, 이를 넘기면 uk_sid_client 와 무관한 위반이 나야 하고 register 는
	// 재확인(existsBySidAndClientId) 결과 행이 없으므로 흡수하지 않고 그대로 전파해야 한다.
	@Test
	void registerPropagatesConstraintViolationsThatAreNotUniqueViolations() {
		String clientIdTooLongForItsColumn = "c".repeat(101);

		assertThatThrownBy(() -> service.register("SID-1", "user-sub-0001", clientIdTooLongForItsColumn))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(repository.findBySid("SID-1")).isEmpty();
	}
}
