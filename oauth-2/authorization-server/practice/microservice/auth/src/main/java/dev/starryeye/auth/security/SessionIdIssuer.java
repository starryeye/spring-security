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
	 *
	 * 주의. issue 는 멱등이고 renew 는 항상 새로 만든다 — 용도가 다르다. authorize 경로는 이미 로그인된
	 *      세션에서 여러 RP 가 같은 sid 를 봐야 하므로 issue 를 쓴다. 로그인 성공 핸들러는 새 OP 세션의
	 *      시작이므로 renew 를 써야 한다. Spring Security 의 세션 고정 방어(changeSessionId)는 세션 id 만
	 *      바꾸고 세션 속성은 그대로 옮기므로, 로그인 성공 시점에도 issue 를 쓰면 로그아웃 없이 다른 사용자로
	 *      재로그인했을 때 이전 사용자의 sid 를 그대로 물려받는다.
	 */

	static final String SESSION_ATTRIBUTE = "OP_SID";

	private static final int BYTE_LENGTH = 16; // base64url 22자
	private static final SecureRandom RANDOM = new SecureRandom();

	public String issue(HttpSession session) {
		String existing = currentSid(session);
		if (existing != null) {
			return existing;
		}
		return renew(session);
	}

	public String renew(HttpSession session) {
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
