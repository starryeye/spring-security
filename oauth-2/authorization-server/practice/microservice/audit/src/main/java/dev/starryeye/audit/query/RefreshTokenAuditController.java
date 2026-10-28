package dev.starryeye.audit.query;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequiredArgsConstructor
public class RefreshTokenAuditController {

	/**
	 * 운영자가 한 사용자의 refresh token 이력을 계열별로 읽는 API. 게이트웨이(:9000)에 노출하지 않고 audit:8089 로
	 *      직접 부른다.
	 *
	 * 주의. 보호(Bearer access token, scope audit.read)는 SecurityConfig 가 한다. 이 컨트롤러는 판정 기준 시각으로
	 *      Instant.now() 를 넘기기만 한다.
	 */

	private final RefreshTokenFamilyQuery query;

	@GetMapping("/audit/users/{sub}/refresh-token-families")
	public RefreshTokenFamiliesResponse families(@PathVariable String sub) {
		return query.familiesOf(sub, Instant.now());
	}
}
