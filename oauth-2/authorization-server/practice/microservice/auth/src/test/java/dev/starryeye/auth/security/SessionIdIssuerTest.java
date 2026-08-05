package dev.starryeye.auth.security;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;

class SessionIdIssuerTest {

	private final SessionIdIssuer issuer = new SessionIdIssuer();

	// 같은 세션에서 여러 RP 가 authorize 해도 sid 는 하나여야 한다. OP 세션 하나 = sid 하나다.
	@Test
	void issuesTheSameSidForOneSession() {
		HttpSession session = new MockHttpSession();

		String first = issuer.issue(session);
		String second = issuer.issue(session);

		assertThat(second).isEqualTo(first);
	}

	@Test
	void issuesDifferentSidForDifferentSessions() {
		assertThat(issuer.issue(new MockHttpSession()))
				.isNotEqualTo(issuer.issue(new MockHttpSession()));
	}

	// sid 는 HTTP 세션 id 가 아니다. id token 에 실려 RP 로 나가고 로그에도 남으므로,
	// 실제 세션 id 를 노출하면 세션 탈취 표면이 된다.
	@Test
	void sidIsNotTheHttpSessionId() {
		MockHttpSession session = new MockHttpSession();

		assertThat(issuer.issue(session)).isNotEqualTo(session.getId());
	}

	@Test
	void sidIsUrlSafeAndLongEnoughToResistGuessing() {
		String sid = issuer.issue(new MockHttpSession());

		assertThat(sid).matches("[A-Za-z0-9_-]+").hasSizeGreaterThanOrEqualTo(22);
	}

	@Test
	void currentSidIsNullBeforeIssue() {
		assertThat(issuer.currentSid(new MockHttpSession())).isNull();
	}

	@Test
	void currentSidReturnsIssuedValue() {
		HttpSession session = new MockHttpSession();
		String issued = issuer.issue(session);

		assertThat(issuer.currentSid(session)).isEqualTo(issued);
	}

	// 로그인은 새 OP 세션의 시작이다. 세션 고정 방어(changeSessionId)가 이전 sid 속성을 그대로 옮겨도,
	// renew 는 그 값을 무시하고 항상 새 sid 를 만들어야 재로그인이 이전 세션의 sid 를 물려받지 않는다.
	@Test
	void renewAlwaysIssuesANewSidEvenIfOneAlreadyExists() {
		HttpSession session = new MockHttpSession();
		String first = issuer.issue(session);

		String renewed = issuer.renew(session);

		assertThat(renewed).isNotEqualTo(first);
		assertThat(issuer.currentSid(session)).isEqualTo(renewed);
	}
}
