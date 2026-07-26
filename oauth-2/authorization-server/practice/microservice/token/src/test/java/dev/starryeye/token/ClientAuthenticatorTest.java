package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.ClientRegistryClient;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientAuthenticatorTest {

	private final ClientRegistryClient clientRegistryClient = mock(ClientRegistryClient.class);
	private final ClientAuthenticator authenticator = new ClientAuthenticator(clientRegistryClient);

	private String basic(String clientId, String secret) {
		return "Basic " + Base64.getEncoder()
				.encodeToString((clientId + ":" + secret).getBytes(StandardCharsets.UTF_8));
	}

	private ClientInfo clientInfo() {
		String hash = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("secret");
		return new ClientInfo("my-client", List.of("http://127.0.0.1:8080/callback"),
				List.of("openid"), hash, List.of("authorization_code"));
	}

	@Test
	void authenticatesValidCredentials() {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());

		ClientInfo result = authenticator.authenticate(basic("my-client", "secret"));

		assertThat(result.clientId()).isEqualTo("my-client");
	}

	@Test
	void missingHeaderIsRejected() {
		assertThatThrownBy(() -> authenticator.authenticate(null))
				.isInstanceOf(ClientAuthenticator.ClientAuthenticationException.class)
				.hasMessage("missing client credentials");
	}

	@Test
	void nonBasicSchemeIsRejected() {
		assertThatThrownBy(() -> authenticator.authenticate("Bearer abc"))
				.isInstanceOf(ClientAuthenticator.ClientAuthenticationException.class)
				.hasMessage("missing client credentials");
	}

	@Test
	void malformedBase64IsRejected() {
		assertThatThrownBy(() -> authenticator.authenticate("Basic !!!not-base64!!!"))
				.isInstanceOf(ClientAuthenticator.ClientAuthenticationException.class)
				.hasMessage("missing client credentials");
	}

	@Test
	void unknownClientIsRejected() {
		when(clientRegistryClient.getClient("ghost")).thenThrow(new ClientRegistryClient.ClientNotFoundException());

		assertThatThrownBy(() -> authenticator.authenticate(basic("ghost", "secret")))
				.isInstanceOf(ClientAuthenticator.ClientAuthenticationException.class)
				.hasMessage("unknown client");
	}

	@Test
	void wrongSecretIsRejected() {
		when(clientRegistryClient.getClient("my-client")).thenReturn(clientInfo());

		assertThatThrownBy(() -> authenticator.authenticate(basic("my-client", "wrong")))
				.isInstanceOf(ClientAuthenticator.ClientAuthenticationException.class)
				.hasMessage("bad client credentials");
	}
}
