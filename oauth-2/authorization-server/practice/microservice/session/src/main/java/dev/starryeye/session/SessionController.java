package dev.starryeye.session;

import dev.starryeye.session.dto.LogoutRequest;
import dev.starryeye.session.dto.RegisterSessionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SessionController {

	/**
	 * OP 세션 레지스트리의 내부 API. token 이 등록하고 auth 가 로그아웃을 통지한다.
	 *
	 * 주의. 로그아웃은 즉시 200 을 돌려주고 발송은 비동기다. 사용자의 로그아웃 응답이 RP 들의 응답 속도에
	 *      묶이면 안 된다.
	 */

	private final SessionService sessionService;
	private final LogoutTokenSender logoutTokenSender;

	@PostMapping("/internal/sessions")
	public ResponseEntity<Void> register(@RequestBody RegisterSessionRequest request) {
		sessionService.register(request.sid(), request.sub(), request.clientId());
		return ResponseEntity.ok().build();
	}

	@PostMapping("/internal/sessions/logout")
	public ResponseEntity<Void> logout(@RequestBody LogoutRequest request) {
		LogoutTargets targets = sessionService.consumeForLogout(request.sid());
		if (!targets.clientIds().isEmpty()) {
			logoutTokenSender.send(request.sid(), targets.sub(), targets.clientIds());
		}
		return ResponseEntity.ok().build();
	}
}
