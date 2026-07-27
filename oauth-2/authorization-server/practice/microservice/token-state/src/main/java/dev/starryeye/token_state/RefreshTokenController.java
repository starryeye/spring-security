package dev.starryeye.token_state;

import dev.starryeye.token_state.dto.IntrospectRequest;
import dev.starryeye.token_state.dto.IssueRequest;
import dev.starryeye.token_state.dto.RevokeRequest;
import dev.starryeye.token_state.dto.RevokeResponse;
import dev.starryeye.token_state.dto.RotateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RefreshTokenController {

	/**
	 * refresh token 상태 API 다. (내부 전용.. gateway 에 노출하지 않는다)
	 *      판정은 하지 않고 RefreshTokenService 에 위임하기만 한다. 상태 전이 규칙이 두 곳에 흩어지면
	 *      트랜잭션 경계 밖에서 판단이 일어나 원자성이 깨진다.
	 *
	 * 주의. 회전 실패 사유(REUSE_DETECTED / EXPIRED / NOT_FOUND ...)를 그대로 돌려주지만, 이것을 클라이언트에게
	 *      전달하는 것은 token 서비스의 몫이 아니다. token 은 전부 invalid_grant 로 뭉갠다.
	 *      사유를 구분해 주면 "이건 이미 소진됐다" 와 "이건 없다" 를 알려주는 셈이라 탐색을 돕는다.
	 *      예외는 SCOPE_EXCEEDED 하나이며, 토큰이 아니라 요청이 잘못된 경우라 invalid_scope 로 나간다(RFC 6749 6).
	 *
	 * 주의. 축소 요청(requestedScope)의 검증도 회전과 같은 트랜잭션 안에서 한다. 호출자가 회전 응답을 받은 뒤에
	 *      검사하면 이미 이전 토큰이 소진된 상태라, 거절과 동시에 새 토큰 원문을 버리게 된다. 원문은 이 응답에서만
	 *      나오므로 그 grant 는 회수 불가다.
	 */

	private final RefreshTokenService refreshTokenService;

	@PostMapping("/internal/refresh-tokens")
	public IssueResult issue(@RequestBody IssueRequest request) {
		return refreshTokenService.issue(request.clientId(), request.sub(), request.scope(), request.authTime());
	}

	@PostMapping("/internal/refresh-tokens/rotate")
	public RotateResult rotate(@RequestBody RotateRequest request) {
		return refreshTokenService.rotate(request.refreshToken(), request.clientId(), request.requestedScope());
	}

	@PostMapping("/internal/refresh-tokens/revoke")
	public RevokeResponse revoke(@RequestBody RevokeRequest request) {
		return new RevokeResponse(refreshTokenService.revoke(request.refreshToken(), request.clientId()));
	}

	@PostMapping("/internal/refresh-tokens/introspect")
	public IntrospectResult introspect(@RequestBody IntrospectRequest request) {
		return refreshTokenService.introspect(request.refreshToken());
	}
}
