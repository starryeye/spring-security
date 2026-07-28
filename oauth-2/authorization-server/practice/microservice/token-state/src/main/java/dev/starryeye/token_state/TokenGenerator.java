package dev.starryeye.token_state;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class TokenGenerator {

	/**
	 * refresh token 원문을 만들고 저장용 해시를 계산한다.
	 *      원문은 256비트 난수라 추측할 수 없고, 저장은 SHA-256 해시만 한다.
	 *
	 * 주의. bcrypt 계열을 쓸 수 없다. 해시로 행을 찾아야 하므로 같은 입력이 항상 같은 출력이어야 하는데,
	 *      bcrypt 는 salt 를 섞어 매번 다른 값을 낸다. 원문이 고엔트로피 난수라 사전 공격 대상이 아니어서
	 *      salt 없는 단순 해시로 충분하다. 사용자 비밀번호에는 같은 논리를 적용할 수 없다.
	 */

	private static final int TOKEN_BYTES = 32; // 256비트

	private final SecureRandom secureRandom = new SecureRandom();

	public String generate() {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				hex.append(String.format("%02x", b));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
}
