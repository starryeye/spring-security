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
	// 주의. 이 테스트는 두 인자 위치를 구분하지 못한다. sub 와 aud 가 같은 값이라 resource indicator 로
	// aud 가 달라지기 전까지는 구분이 불가능하다.
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

	// client_scopes 자체에 openid 가 잘못 들어가 있어도(관리자 실수), containsAll 은 그것만으로는 막지 못한다
	// (요청 scope 가 client_scopes 의 부분집합이라는 사실 자체는 참이기 때문). 이 테스트는 그 상황에서도
	// 사용자 위임 scope 가드가 별도로 막는지를 확인한다 — 가드가 없으면 이 테스트만으로는 통과해 버린다.
	@Test
	void requestedOpenidScopeIsRejectedEvenWhenClientScopesAllowsIt() {
		ClientInfo client = new ClientInfo("my-client", List.of(), List.of(), "{bcrypt}x",
				List.of("client_credentials"), List.of("openid", "introspect"));

		GrantResult result = service.grant(client, "openid");

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("invalid_scope");
		verify(accessTokenIssuer, never()).issue(any(), any(), any());
	}

	// scope 파라미터를 생략해 client_scopes 전부가 기본값이 되는 경로에서도 같은 가드가 적용돼야 한다 —
	// client_scopes 자체가 잘못 설정된 것을 조용히 통과시키지 않고 첫 사용에서 드러내는 것이 의도다.
	@Test
	void defaultScopeIsRejectedWhenClientScopesItselfContainsUserDelegatedScope() {
		ClientInfo client = new ClientInfo("my-client", List.of(), List.of(), "{bcrypt}x",
				List.of("client_credentials"), List.of("openid"));

		GrantResult result = service.grant(client, null);

		assertThat(result.success()).isFalse();
		assertThat(result.error()).isEqualTo("invalid_scope");
		verify(accessTokenIssuer, never()).issue(any(), any(), any());
	}
}
