package dev.starryeye.session;

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

	@Autowired MockMvc mockMvc;
	@Autowired OidcSessionEntityRepository repository;
	@Autowired SessionService sessionService;
	@MockitoBean LogoutTokenSender logoutTokenSender;

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

		verify(logoutTokenSender).send(eq("SID-1"), eq("user-sub-0001"),
				argThat(ids -> ids.containsAll(List.of("demo-rp", "other-rp")) && ids.size() == 2));
		assertThat(repository.findBySid("SID-1")).isEmpty();
	}

	// 세션이 없어도 200 이다. 이미 로그아웃한 사용자가 다시 로그아웃하는 것은 오류가 아니다.
	@Test
	void logoutOfUnknownSessionSucceedsWithoutDispatch() throws Exception {
		mockMvc.perform(post("/internal/sessions/logout").contentType(MediaType.APPLICATION_JSON)
						.content("{\"sid\":\"SID-NONE\"}"))
				.andExpect(status().isOk());

		verify(logoutTokenSender, never()).send(any(), any(), any());
	}
}
