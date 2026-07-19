package dev.starryeye.user_directory;

import dev.starryeye.user_directory.jpa.UserEntity;
import dev.starryeye.user_directory.jpa.UserEntityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
class UserControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockBean
	UserEntityRepository repository;

	private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

	private UserEntity seedUser(String authorities) {
		return UserEntity.builder()
				.sub("user-sub-0001").username("user")
				.password(encoder.encode("1111")).authorities(authorities).build();
	}

	@Test
	void authenticateWithValidCredentialsReturnsSubAndAuthorities() throws Exception {
		when(repository.findByUsername("user")).thenReturn(Optional.of(seedUser("ROLE_USER")));

		mockMvc.perform(post("/internal/users/authenticate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"user\",\"password\":\"1111\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sub").value("user-sub-0001"))
				.andExpect(jsonPath("$.authorities[0]").value("ROLE_USER"));
	}

	@Test
	void authenticateWithWrongPasswordReturns401() throws Exception {
		when(repository.findByUsername("user")).thenReturn(Optional.of(seedUser("ROLE_USER")));

		mockMvc.perform(post("/internal/users/authenticate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"user\",\"password\":\"wrong\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void authenticateUnknownUserReturns401() throws Exception {
		when(repository.findByUsername("ghost")).thenReturn(Optional.empty());

		mockMvc.perform(post("/internal/users/authenticate")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"ghost\",\"password\":\"x\"}"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void getUserReturnsProfileForKnownSub() throws Exception {
		when(repository.findBySub("user-sub-0001")).thenReturn(Optional.of(seedUser("ROLE_USER,ROLE_ADMIN")));

		mockMvc.perform(get("/internal/users/user-sub-0001"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username").value("user"))
				.andExpect(jsonPath("$.authorities", hasSize(2)));
	}

	@Test
	void getUserUnknownSubReturns404() throws Exception {
		when(repository.findBySub("nope")).thenReturn(Optional.empty());

		mockMvc.perform(get("/internal/users/nope"))
				.andExpect(status().isNotFound());
	}
}
