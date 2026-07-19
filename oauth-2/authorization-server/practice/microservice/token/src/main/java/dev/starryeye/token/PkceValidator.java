package dev.starryeye.token;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class PkceValidator {

	/**
	 * PKCE(S256) 검증이다.
	 *      challenge = BASE64URL( SHA256( verifier ) ). auth 가 저장한 challenge 와 token 이 받은 verifier 로 대조한다.
	 */

	public boolean matches(String codeVerifier, String storedChallenge) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
			String computed = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
			return computed.equals(storedChallenge);
		} catch (Exception e) {
			return false;
		}
	}
}
