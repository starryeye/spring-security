package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientCredentialsGrantServiceTest {

	private final AccessTokenIssuer accessTokenIssuer = mock(AccessTokenIssuer.class);

	private final ClientCredentialsGrantService service =
			new ClientCredentialsGrantService(accessTokenIssuer, 300L);

	private ClientInfo articleApi() {
		return new ClientInfo("article-api", List.of(), List.of(), "{bcrypt}x",
				List.of("client_credentials"), List.of("introspect"));
	}

	@Test
	void issuesAccessTokenWithRequestedScope() {
		when(accessTokenIssuer.issue("article-api", "article-api", "introspect")).thenReturn("signed-token");

		GrantResult result = service.grant(articleApi(), "introspect");

		assertThat(result.success()).isTrue();
		assertThat(result.response().access_token()).isEqualTo("signed-token");
		assertThat(result.response().scope()).isEqualTo("introspect");
		assertThat(result.response().token_type()).isEqualTo("Bearer");
	}

	// RFC 6749 4.4.3 — refresh token 을 주지 않는다. 사용자가 없으므로 "재로그인 없이 연장" 이라는
	// refresh 의 존재 이유가 성립하지 않고, 자격증명으로 다시 받으면 된다.
	@Test
	void issuesNeitherRefreshTokenNorIdToken() {
		when(accessTokenIssuer.issue(any(), any(), any())).thenReturn("signed-token");

		GrantResult result = service.grant(articleApi(), "introspect");

		assertThat(result.response().refresh_token()).isNull();
		assertThat(result.response().id_token()).isNull();
	}

	// RFC 9068 — 사용자가 없으므로 sub 는 client_id 다.
	@Test
	void subjectIsTheClientItself() {
		when(accessTokenIssuer.issue("article-api", "article-api", "introspect")).thenReturn("signed-token");

		service.grant(articleApi(), "introspect");

		verify(accessTokenIssuer).issue("article-api", "article-api", "introspect");
	}

	// RFC 6749 3.3 — 생략 시 사전 정의된 기본값. authorization_code 경로와 같은 규칙이다.
	@Test
	void omittedScopeDefaultsToAllClientScopes() {
		ClientInfo client = new ClientInfo("article-api", List.of(), List.of(), "{bcrypt}x",
				List.of("client_credentials"), List.of("introspect", "audit"));
		when(accessTokenIssuer.issue(any(), any(), any())).thenReturn("signed-token");

		GrantResult result = service.grant(client, null);

		assertThat(result.response().scope()).isEqualTo("introspect audit");
		verify(accessTokenIssuer).issue("article-api", "article-api", "introspect audit");
	}

	@Test
	void scopeBeyondClientScopesIsRejected() {
		GrantResult result = service.grant(articleApi(), "introspect admin");

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("invalid_scope");
		verify(accessTokenIssuer, never()).issue(any(), any(), any());
	}

	// 사용자 위임 scope 는 이 grant 로 받을 수 없다. openid 는 scopes 컬럼에 있고 clientScopes 에는 없다.
	@Test
	void userDelegatedScopeIsRejected() {
		ClientInfo client = new ClientInfo("my-client", List.of(), List.of("openid", "profile"), "{bcrypt}x",
				List.of("client_credentials"), List.of());

		GrantResult result = service.grant(client, "openid");

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("invalid_scope");
	}

	@Test
	void clientWithNoClientScopesIsRejected() {
		ClientInfo client = new ClientInfo("my-client", List.of(), List.of("openid"), "{bcrypt}x",
				List.of("client_credentials"), List.of());

		GrantResult result = service.grant(client, null);

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("invalid_scope");
		verify(accessTokenIssuer, never()).issue(any(), any(), any());
	}

	@Test
	void clientWithoutTheGrantIsRejected() {
		ClientInfo client = new ClientInfo("my-client", List.of(), List.of("openid"), "{bcrypt}x",
				List.of("authorization_code"), List.of("introspect"));

		GrantResult result = service.grant(client, "introspect");

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("unauthorized_client");
		verify(accessTokenIssuer, never()).issue(any(), any(), any());
	}
}
