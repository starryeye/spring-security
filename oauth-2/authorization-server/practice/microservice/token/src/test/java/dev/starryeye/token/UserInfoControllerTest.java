package dev.starryeye.token;

import dev.starryeye.token.client.UserDirectoryClient;
import dev.starryeye.token.client.UserProfile;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// ProfileClaimMapper 는 mock 이 아니라 실제 구현을 쓴다. scope->claim 매핑이 검증 대상이기 때문이다.
@WebMvcTest(UserInfoController.class)
@Import(ProfileClaimMapper.class)
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

		// 조회해봐야 전부 버릴 claim 이므로 user-directory 를 호출하지 않는다 (불필요한 왕복 + 무관한 가용성 결합 제거)
		verify(userDirectoryClient, never()).getUser(anyString());
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

	// 검증기가 InvalidTokenException 이 아닌 예외를 던지면(= signing 장애 등 우리 쪽 문제) 토큰 탓을 하면 안 된다.
	// 401 을 주면 RP 는 멀쩡한 토큰을 버리고 재인증을 돌린다. OAuth2ExceptionHandler 가 500 server_error 로 정규화한다.
	@Test
	void signingFailureReturns500NotUnauthorized() throws Exception {
		when(accessTokenVerifier.verify("tok")).thenThrow(new IllegalStateException("signing is down"));

		mockMvc.perform(get("/userinfo").header("Authorization", "Bearer tok"))
				.andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.error").value("server_error"));
	}

	@Test
	void tokenWithoutOpenidScopeReturns403InsufficientScope() throws Exception {
		when(accessTokenVerifier.verify("tok")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("user-sub-0001", List.of("profile")));

		mockMvc.perform(get("/userinfo").header("Authorization", "Bearer tok"))
				.andExpect(status().isForbidden())
				.andExpect(header().string("WWW-Authenticate", "Bearer error=\"insufficient_scope\""));
	}

	// 일시 장애(연결 실패·5xx): 사용자 존재 여부가 미확정이므로 표준 필수 claim 인 sub 만이라도 돌려준다.
	@Test
	void returnsOnlySubWhenUserDirectoryFails() throws Exception {
		when(accessTokenVerifier.verify("tok")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("user-sub-0001", List.of("openid", "profile", "email")));
		when(userDirectoryClient.getUser("user-sub-0001")).thenThrow(new RuntimeException("user-directory down"));

		mockMvc.perform(get("/userinfo").header("Authorization", "Bearer tok"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sub").value("user-sub-0001"))
				.andExpect(jsonPath("$.name").doesNotExist())
				.andExpect(jsonPath("$.email").doesNotExist());
	}

	// 404 는 "사용자가 없다"는 확정된 사실이다. 주체가 사라진 토큰은 실효이므로 degrade 가 아니라 401 이다.
	@Test
	void deletedUserReturns401InvalidToken() throws Exception {
		when(accessTokenVerifier.verify("tok")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("user-sub-0001", List.of("openid", "profile")));
		when(userDirectoryClient.getUser("user-sub-0001"))
				.thenThrow(new UserDirectoryClient.UserNotFoundException());

		mockMvc.perform(get("/userinfo").header("Authorization", "Bearer tok"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_token\""));
	}

	// OIDC Core 5.3.1: userinfo 는 GET 과 POST 를 모두 지원해야 한다(MUST).
	@Test
	void postWithBearerHeaderIsSupported() throws Exception {
		when(accessTokenVerifier.verify("tok")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("user-sub-0001", List.of("openid")));

		mockMvc.perform(post("/userinfo").header("Authorization", "Bearer tok"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sub").value("user-sub-0001"));
	}

	// RFC 6750 2.2: form-encoded body 의 access_token 파라미터로도 토큰을 실을 수 있다.
	@Test
	void postWithFormEncodedAccessTokenIsSupported() throws Exception {
		when(accessTokenVerifier.verify("tok")).thenReturn(
				new AccessTokenVerifier.VerifiedToken("user-sub-0001", List.of("openid")));

		mockMvc.perform(post("/userinfo")
						.contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.param("access_token", "tok"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sub").value("user-sub-0001"));
	}

	// RFC 6750 이 권장하지 않는 쿼리 파라미터 전달은 지원하지 않는다 (로그·Referer 에 토큰이 남는다)
	@Test
	void queryParameterAccessTokenIsNotAccepted() throws Exception {
		mockMvc.perform(get("/userinfo").param("access_token", "tok"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string("WWW-Authenticate", "Bearer"));

		verify(accessTokenVerifier, never()).verify(anyString());
	}

	// RFC 6750 2: 한 요청이 두 가지 전달 방식을 동시에 쓰면 오류다.
	@Test
	void bothHeaderAndFormTokenReturns400InvalidRequest() throws Exception {
		mockMvc.perform(post("/userinfo")
						.header("Authorization", "Bearer tok")
						.contentType(MediaType.APPLICATION_FORM_URLENCODED)
						.param("access_token", "tok"))
				.andExpect(status().isBadRequest())
				.andExpect(header().string("WWW-Authenticate", "Bearer error=\"invalid_request\""));

		verify(accessTokenVerifier, never()).verify(anyString());
	}
}
