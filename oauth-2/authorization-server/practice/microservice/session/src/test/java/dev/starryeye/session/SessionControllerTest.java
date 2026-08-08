package dev.starryeye.session;

import dev.starryeye.session.event.LogoutEventPublisher;
import dev.starryeye.session.jpa.OidcSessionEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SessionControllerTest {

	// 주의. 이 클래스도 SessionServiceTest 와 같은 이유로 LogoutEventPublisher 를 MockitoBean 으로 끊는다 —
	//      logoutDispatchesToEveryClientOfThatSession 과 logoutOfUnknownSessionSucceedsWithoutDispatch 가
	//      /internal/sessions/logout 을 거쳐 실제 SessionService.consumeForLogout 을 타므로, Kafka 브로커가
	//      없으면 이 클래스도 producer 의 max.block.ms 만큼 멈췄다가 실패한다.
	@Autowired MockMvc mockMvc;
	@Autowired OidcSessionEntityRepository repository;
	@Autowired SessionService sessionService;
	@MockitoBean LogoutTokenSender logoutTokenSender;
	@MockitoBean LogoutEventPublisher logoutEventPublisher;

	@BeforeEach
	void clean() {
		repository.deleteAll();
		reset(logoutTokenSender);
	}

	@Test
	void registerStoresTheSession() throws Exception {
		mockMvc.perform(post("/internal/sessions").contentType(MediaType.APPLICATION_JSON)
						.content("{\"sid\":\"SID-1\",\"sub\":\"user-sub-0001\",\"clientId\":\"demo-rp\"}"))
				.andExpect(status().isOk());

		assertThat(repository.findBySid("SID-1")).hasSize(1);
	}

	@Test
	void logoutDispatchesToEveryClientOfThatSession() throws Exception {
		sessionService.register("SID-1", "user-sub-0001", "demo-rp");
		sessionService.register("SID-1", "user-sub-0001", "other-rp");

		mockMvc.perform(post("/internal/sessions/logout").contentType(MediaType.APPLICATION_JSON)
						.content("{\"sid\":\"SID-1\"}"))
				.andExpect(status().isOk());

		verify(logoutTokenSender).send(eq("SID-1"), argThat(targets -> targets.size() == 2
				&& targets.containsAll(List.of(
						new LogoutTargets.Target("demo-rp", "user-sub-0001"),
						new LogoutTargets.Target("other-rp", "user-sub-0001")))));
		assertThat(repository.findBySid("SID-1")).isEmpty();
	}

	// 세션이 없어도 200 이다. 이미 로그아웃한 사용자가 다시 로그아웃하는 것은 오류가 아니다.
	@Test
	void logoutOfUnknownSessionSucceedsWithoutDispatch() throws Exception {
		mockMvc.perform(post("/internal/sessions/logout").contentType(MediaType.APPLICATION_JSON)
						.content("{\"sid\":\"SID-NONE\"}"))
				.andExpect(status().isOk());

		verify(logoutTokenSender, never()).send(any(), any());
	}
}
