package dev.starryeye.session;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogoutTokenSender {

	/**
	 * 각 RP 의 backchannel_logout_uri 로 logout token 을 form POST 한다. (Back-Channel Logout 1.0 2.5)
	 *
	 * 주의. best-effort 다. 실패는 로그만 남기고 재시도하지 않는다. 세션은 이미 끝났으므로 발송 실패가
	 *      로그아웃을 되돌리지는 않는다 — 다만 그 RP 의 세션은 살아남는다.
	 *
	 * 주의. 한 RP 의 실패가 나머지 발송을 막지 않도록 client 단위로 예외를 가둔다.
	 */

	private final LogoutTokenDelivery delivery;

	@Async
	public void send(String sid, List<LogoutTargets.Target> targets) {
		for (LogoutTargets.Target target : targets) {
			try {
				delivery.deliver(sid, target.sub(), target.clientId());
			} catch (Exception e) {
				log.warn("logout token 발송 실패. sid={} clientId={}", sid, target.clientId(), e);
			}
		}
	}
}
