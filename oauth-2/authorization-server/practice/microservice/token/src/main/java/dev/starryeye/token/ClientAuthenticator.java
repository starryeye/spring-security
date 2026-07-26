package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.ClientRegistryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
@RequiredArgsConstructor
public class ClientAuthenticator {

	/**
	 * Authorization 헤더의 Basic 자격증명으로 client 를 인증한다.
	 *      token · introspection · revocation 세 엔드포인트가 같은 절차를 쓰므로 여기 한 곳에 둔다.
	 *
	 * 주의. 실패 사유(헤더 없음 / 미등록 client / 틀린 secret)를 예외 메시지로 구분하지만,
	 *      셋 다 401 invalid_client 로 응답한다. 상태 코드를 갈라 주면 어떤 client_id 가 등록돼 있는지
	 *      알려주는 셈이라 열거 공격을 돕는다.
	 */

	private final ClientRegistryClient clientRegistryClient;
	private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

	public static class ClientAuthenticationException extends RuntimeException {
		public ClientAuthenticationException(String message) {
			super(message);
		}
	}

	public ClientInfo authenticate(String authorizationHeader) {

		String[] credentials = parseBasic(authorizationHeader);
		if (credentials == null) {
			throw new ClientAuthenticationException("missing client credentials");
		}

		ClientInfo client;
		try {
			client = clientRegistryClient.getClient(credentials[0]);
		} catch (ClientRegistryClient.ClientNotFoundException e) {
			throw new ClientAuthenticationException("unknown client");
		}
		if (client == null || !passwordEncoder.matches(credentials[1], client.clientSecretHash())) {
			throw new ClientAuthenticationException("bad client credentials");
		}
		return client;
	}

	private String[] parseBasic(String authorization) {
		if (authorization == null || !authorization.startsWith("Basic ")) {
			return null;
		}
		String decoded;
		try {
			decoded = new String(Base64.getDecoder().decode(authorization.substring(6)), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			return null; // 잘못된 base64 -> invalid_client
		}
		int idx = decoded.indexOf(':');
		if (idx < 0) {
			return null;
		}
		return new String[]{decoded.substring(0, idx), decoded.substring(idx + 1)};
	}
}
