package dev.starryeye.auth.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

@Component
public class SessionIdIssuer {

	/**
	 * OP 세션 식별자(sid)를 만들어 세션 속성에 담는다. (OIDC Back-Channel Logout 1.0 의 sid claim)
	 *      로그인 한 번에 sid 하나가 나오고, 그 세션에서 authorize 하는 모든 RP 가 같은 sid 를 받는다.
	 *
	 * 주의. sid 는 HTTP 세션 id 가 아니다. sid 는 id token 에 실려 RP 로 나가고 로그에도 남으므로,
	 *      실제 세션 id 를 그대로 쓰면 세션 탈취 표면이 된다.
	 *
	 * 주의. 추측 가능한 값이면 남의 세션을 지목하는 logout token 을 위조할 근거가 되므로 SecureRandom 을 쓴다.
	 */

	static final String SESSION_ATTRIBUTE = "OP_SID";

	private static final int BYTE_LENGTH = 16; // base64url 22자
	private static final SecureRandom RANDOM = new SecureRandom();

	public String issue(HttpSession session) {
		String existing = currentSid(session);
		if (existing != null) {
			return existing;
		}
		byte[] bytes = new byte[BYTE_LENGTH];
		RANDOM.nextBytes(bytes);
		String sid = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		session.setAttribute(SESSION_ATTRIBUTE, sid);
		return sid;
	}

	public String currentSid(HttpSession session) {
		return (String) session.getAttribute(SESSION_ATTRIBUTE);
	}
}
