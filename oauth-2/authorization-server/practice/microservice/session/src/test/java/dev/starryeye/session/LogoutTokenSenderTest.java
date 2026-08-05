package dev.starryeye.session;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LogoutTokenSenderTest {

	private final LogoutTokenDelivery delivery = mock(LogoutTokenDelivery.class);
	private final LogoutTokenSender sender = new LogoutTokenSender(delivery);

	// README·설계가 명시한 계약: 한 RP 의 실패가 나머지 발송을 막지 않는다. e2e 는 RP 가 하나뿐이라 이
	// 계약을 검증하지 못했다 — 가운데 client 의 delivery 가 예외를 던져도 앞뒤 client 는 발송돼야 한다.
	@Test
	void oneClientsFailureDoesNotBlockDeliveryToTheRest() {
		doThrow(new RuntimeException("rp2 unreachable"))
				.when(delivery).deliver("SID-1", "user-sub-0002", "rp2");

		sender.send("SID-1", List.of(
				new LogoutTargets.Target("rp1", "user-sub-0001"),
				new LogoutTargets.Target("rp2", "user-sub-0002"),
				new LogoutTargets.Target("rp3", "user-sub-0003")));

		verify(delivery).deliver("SID-1", "user-sub-0001", "rp1");
		verify(delivery).deliver("SID-1", "user-sub-0002", "rp2");
		verify(delivery).deliver("SID-1", "user-sub-0003", "rp3");
	}
}
