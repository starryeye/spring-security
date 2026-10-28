package dev.starryeye.token_state;

import dev.starryeye.token_state.event.RefreshTokenEventPublisher;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class RefreshTokenEventAtomicityTest {

	/**
	 * 사건을 outbox 에 쓰지 못하면 상태 변경도 없던 일이 된다 — outbox 를 두는 이유가 이 원자성이다.
	 *      RefreshTokenEventPublisher 를 스파이로 감싸 사건 기록만 실패시키고, 같은 트랜잭션에서 이미
	 *      저장한 refresh_tokens 행이 함께 롤백되는지 본다.
	 *
	 * 주의. 이 클래스는 @MockitoSpyBean 때문에 자기만의 컨텍스트를 갖는다. h2 는 다른 컨텍스트와 공유하는
	 *      이름 있는 인메모리 DB 라, 다른 클래스가 남긴 행이 있어도 행 수 "차이"만 보도록 실패시키기 전의
	 *      개수를 먼저 잡는다.
	 */

	@Autowired
	private RefreshTokenService service;

	@Autowired
	private RefreshTokenEntityRepository repository;

	@MockitoSpyBean
	private RefreshTokenEventPublisher eventPublisher;

	@Test
	@DisplayName("ISSUED 를 쓰지 못하면 발급도 없던 일이 된다")
	void issueRollsBackWhenTheEventCannotBeRecorded() {
		doThrow(new IllegalStateException("outbox unavailable")).when(eventPublisher).issued(any(), any());
		long refreshRows = repository.count();

		assertThatThrownBy(() -> service.issue("my-client", "user-sub-0001", "openid", 1000L, "SID-A"))
				.isInstanceOf(IllegalStateException.class);

		assertThat(repository.count()).isEqualTo(refreshRows);
	}
}
