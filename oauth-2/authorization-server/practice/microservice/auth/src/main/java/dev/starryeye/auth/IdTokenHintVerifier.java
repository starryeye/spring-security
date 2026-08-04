package dev.starryeye.auth;

import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.starryeye.auth.client.SigningJwksClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class IdTokenHintVerifier {

	/**
	 * RP-Initiated Logout 의 id_token_hint 를 검증하고 aud(client_id)를 돌려준다.
	 *
	 * 주의. exp 를 검사하지 않는다. 로그아웃 시점에 id token 이 만료돼 있는 것은 정상이며, 만료를 이유로
	 *      거부하면 정당한 로그아웃이 막힌다. 이 저장소에서 만료를 일부러 무시하는 유일한 검증이다.
	 *
	 * 주의. 힌트는 사용자 식별에 쓰지 않는다. 사용자는 브라우저로 오므로 세션 쿠키로 이미 누구인지 안다.
	 *      힌트는 post_logout_redirect_uri 를 어느 client 기준으로 검증할지 정하는 데만 쓴다.
	 */

	private final SigningJwksClient jwksClient;
	private final String issuer;

	public IdTokenHintVerifier(SigningJwksClient jwksClient, @Value("${my.issuer}") String issuer) {
		this.jwksClient = jwksClient;
		this.issuer = issuer;
	}

	public String verify(String idToken) {
		SignedJWT signedJWT;
		JWTClaimsSet claims;
		try {
			signedJWT = SignedJWT.parse(idToken);
			claims = signedJWT.getJWTClaimsSet();
		} catch (Exception e) {
			throw new InvalidHintException("malformed id_token_hint");
		}

		JWKSet jwkSet;
		try {
			jwkSet = JWKSet.parse(jwksClient.jwks());
		} catch (Exception e) {
			throw new InvalidHintException("jwks unavailable"); // 키를 못 구하면 리다이렉트를 포기한다
		}

		try {
			RSAKey key = (RSAKey) jwkSet.getKeyByKeyId(signedJWT.getHeader().getKeyID());
			if (key == null || !signedJWT.verify(new RSASSAVerifier(key.toRSAPublicKey()))) {
				throw new InvalidHintException("signature verification failed");
			}
		} catch (InvalidHintException e) {
			throw e;
		} catch (Exception e) {
			throw new InvalidHintException("signature verification failed");
		}

		if (!issuer.equals(claims.getIssuer())) {
			throw new InvalidHintException("issuer mismatch");
		}

		List<String> audience = claims.getAudience();
		if (audience == null || audience.isEmpty()) {
			throw new InvalidHintException("missing aud");
		}
		return audience.get(0);
	}

	public static class InvalidHintException extends RuntimeException {
		public InvalidHintException(String message) {
			super(message);
		}
	}
}
