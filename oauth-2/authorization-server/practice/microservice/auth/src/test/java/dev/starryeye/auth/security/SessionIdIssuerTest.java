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
}
