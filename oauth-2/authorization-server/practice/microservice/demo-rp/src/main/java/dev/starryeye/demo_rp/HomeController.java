package dev.starryeye.demo_rp;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class HomeController {

	/**
	 * 로그인이 필요한 보호 페이지. back-channel logout 의 성공 판정에 쓰인다.
	 *      로그아웃 전에는 200, OP 가 logout token 을 보낸 뒤에는 302(로그인으로)여야 한다.
	 */

	@GetMapping("/me")
	public Map<String, Object> me(@AuthenticationPrincipal OidcUser user) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("sub", user.getSubject());
		body.put("sid", user.getClaimAsString("sid"));
		return body;
	}
}
