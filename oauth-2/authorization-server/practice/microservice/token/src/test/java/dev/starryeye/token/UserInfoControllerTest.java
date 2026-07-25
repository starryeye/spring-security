package dev.starryeye.token;

import dev.starryeye.token.client.UserDirectoryClient;
import dev.starryeye.token.client.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserInfoController.class)
class UserInfoControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	AccessTokenVerifier accessTokenVerifier;

	@MockitoBean
	UserDirectoryClient userDirectoryClient;

	private UserProfile profile() {
		return new UserProfile("user-sub-0001", "user", List.of("ROLE_USER"),
				"Star Rye", "starry", "starryeye", "starryeye@example.com", true);
	}

	@Test
	void returnsOnlySubWhenScopeIsOpenidOnly() throws Exception {
		when(accessTokenVerifier.verify("tok")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("user-sub-0001", List.of("openid")));
		when(userDirectoryClient.getUser("user-sub-0001")).thenReturn(profile());

		mockMvc.perform(get("/userinfo").header("Authorization", "Bearer tok"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sub").value("user-sub-0001"))
				.andExpect(jsonPath("$.name").doesNotExist())
				.andExpect(jsonPath("$.email").doesNotExist());
	}

	@Test
	void returnsProfileClaimsWhenProfileScopePresent() throws Exception {
		when(accessTokenVerifier.verify("tok")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("user-sub-0001", List.of("openid", "profile")));
		when(userDirectoryClient.getUser("user-sub-0001")).thenReturn(profile());

		mockMvc.perform(get("/userinfo").header("Authorization", "Bearer tok"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Star Rye"))
				.andExpect(jsonPath("$.nickname").value("starry"))
				.andExpect(jsonPath("$.preferred_username").value("starryeye"))
				.andExpect(jsonPath("$.email").doesNotExist());
	}

	@Test
	void returnsEmailClaimsWhenEmailScopePresent() throws Exception {
		when(accessTokenVerifier.verify("tok")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("user-sub-0001", List.of("openid", "email")));
		when(userDirectoryClient.getUser("user-sub-0001")).thenReturn(profile());

		mockMvc.perform(get("/userinfo").header("Authorization", "Bearer tok"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.email").value("starryeye@example.com"))
				.andExpect(jsonPath("$.email_verified").value(true))
				.andExpect(jsonPath("$.name").doesNotExist());
	}

	@Test
	void missingTokenReturns401WithBearerChallenge() throws Exception {
		mockMvc.perform(get("/userinfo"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"));
	}

	@Test
	void invalidTokenReturns401InvalidToken() throws Exception {
		when(accessTokenVerifier.verify("bad")).thenThrow(new AccessTokenVerifier.InvalidTokenException("bad signature"));

		mockMvc.perform(get("/userinfo").header("Authorization", "Bearer bad"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""));
	}

	@Test
	void tokenWithoutOpenidScopeReturns403InsufficientScope() throws Exception {
		when(accessTokenVerifier.verify("tok")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("user-sub-0001", List.of("profile")));

		mockMvc.perform(get("/userinfo").header("Authorization", "Bearer tok"))
				.andExpect(status().isForbidden())
				.andExpect(header().string("WWW-Authenticate", "Bearer error=\"insufficient_scope\""));
	}
}
