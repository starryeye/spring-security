package dev.starryeye.auth.security;

import dev.starryeye.auth.client.UserDirectoryClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// LogoutControllerTest 와 같은 이유로 이 클래스만 SessionAutoConfiguration 을 끈다. Spring Session 의
// SessionRepositoryFilter 는 세션을 쿠키로만 조회하므로(spring-session-core 소스 확인) MockMvc 가
// .session() 으로 붙인 세션을 무시한다. build.gradle 의 test 태스크에는 절대 걸지 않는다 — 그러면 모듈의
// 다른 모든 테스트가 조용히 같은 예외를 상속받는다.
@SpringBootTest(properties = "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.session.SessionAutoConfiguration")
@AutoConfigureMockMvc
class SecurityConfigFormLoginTest {

	@Autowired MockMvc mockMvc;
	@Autowired SessionIdIssuer sessionIdIssuer; // 실제 빈이어야 배선을 검증한다 (mock 이면 renew/issue 구분이 사라진다)
	@MockitoBean UserDirectoryClient userDirectoryClient;

	// formLogin() 의 성공 핸들러가 SecurityConfig 에서 실제로 배선된 채 도는지 대조한다. 같은 HTTP 세션을
	// 유지한 채 로그인이 두 번 일어나면 sid 가 달라야 한다 — 그것이 renew 배선의 정의다. issue 로 되돌리면
	// (멱등) 세션 고정 방어(changeSessionId)가 보존한 첫 로그인의 sid 속성을 그대로 돌려주므로 이 단언이 깨진다.
	//
	// 주의. 두 번째 요청은 SecurityMockMvcRequestBuilders.formLogin().merge(...) 로 세션을 실어 보낼 수 없다.
	// MockMvc.perform() 은 Security 의 defaultRequestBuilder(get("/").with(testSecurityContext()))를 매
	// 요청에 자동으로 merge() 하는데, FormLoginRequestBuilder.merge() 는 필드별 병합이 아니라 parent 참조를
	// 통째로 덮어써서 우리가 직접 건 .merge(sameSessionCarrier) 를 그대로 지워버린다(session 을 실은 요청이
	// 조용히 새 세션으로 바뀐다 — MockHttpSession 의 identityHashCode 로 직접 확인). 반면 표준
	// MockHttpServletRequestBuilder.merge() 는 필드가 이미 채워져 있으면 parent 값으로 덮지 않으므로,
	// post("/login")...session(session) 으로 직접 만들면 같은 자동 merge 를 거쳐도 세션이 유지된다.
	@Test
	void reLoginOnSameHttpSessionIssuesANewSid() throws Exception {
		when(userDirectoryClient.authenticate(anyString(), anyString()))
				.thenReturn(new UserDirectoryClient.AuthenticatedUser("user-sub-0001", List.of("ROLE_USER")));

		MvcResult firstLogin = mockMvc.perform(formLogin().user("user-sub-0001").password("irrelevant"))
				.andExpect(status().is3xxRedirection())
				.andReturn();

		MockHttpSession session = (MockHttpSession) firstLogin.getRequest().getSession(false);
		assertThat(session).isNotNull();
		String firstSid = sessionIdIssuer.currentSid(session);
		assertThat(firstSid).isNotNull();

		// 같은 세션 객체를 두 번째 로그인 요청에 그대로 실어 보낸다. MockHttpSession.changeSessionId() 는 id 만
		// 바꾸고 속성 맵은 그대로 두므로(spring-test 소스 확인), 실제 컨테이너의 세션 고정 방어를 그대로 재현한다.
		mockMvc.perform(post("/login")
						.param("username", "user-sub-0001")
						.param("password", "irrelevant")
						.session(session)
						.with(csrf()))
				.andExpect(status().is3xxRedirection());

		String secondSid = sessionIdIssuer.currentSid(session);
		assertThat(secondSid).isNotNull().isNotEqualTo(firstSid);
	}
}
