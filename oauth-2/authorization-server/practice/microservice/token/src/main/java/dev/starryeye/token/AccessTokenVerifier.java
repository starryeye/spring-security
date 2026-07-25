package dev.starryeye.token;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.starryeye.token.client.SigningClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AccessTokenVerifier {

	/**
	 * userinfo 요청이 실어온 access token 을 자체 검증한다.
	 *      서명은 signing 이 공개하는 jwks 로, 그 외 iss/exp 를 확인한다.
	 *      -> 이 검증 역량이 이미 token 서비스에 있기 때문에 userinfo 를 별도 서비스로 빼지 않았다.
	 *
	 * 주의. jwks 를 매 요청 조회하면 signing 에 부하가 걸린다. 캐시는 이후 개선 항목이다.
	 */

	private final SigningClient signingClient;

	@Value("${my.issuer}")
	private String issuer;

	public record VerifiedToken(String sub, List<String> scopes) {
	}

	public static class InvalidTokenException extends RuntimeException {
		public InvalidTokenException(String message) {
			super(message);
		}
	}

	@SuppressWarnings("unchecked")
	public VerifiedToken verify(String token) {

		SignedJWT signedJWT;
		JWTClaimsSet claims;
		try {
			signedJWT = SignedJWT.parse(token);
			claims = signedJWT.getJWTClaimsSet();
		} catch (Exception e) {
			throw new InvalidTokenException("malformed token");
		}

		try {
			JWKSet jwkSet = JWKSet.parse((Map<String, Object>) signingClient.jwks());
			RSAKey key = (RSAKey) jwkSet.getKeyByKeyId(signedJWT.getHeader().getKeyID());
			if (key == null || !signedJWT.verify(new RSASSAVerifier(key.toRSAPublicKey()))) {
				throw new InvalidTokenException("signature verification failed");
			}
		} catch (InvalidTokenException e) {
			throw e;
		} catch (Exception e) {
			throw new InvalidTokenException("cannot verify signature");
		}

		Date expiration = claims.getExpirationTime();
		if (expiration == null || expiration.before(new Date())) {
			throw new InvalidTokenException("token expired");
		}
		if (!issuer.equals(claims.getIssuer())) {
			throw new InvalidTokenException("issuer mismatch");
		}

		List<String> scopes = new ArrayList<>();
		try {
			List<String> claimScopes = claims.getStringListClaim("scope");
			if (claimScopes != null) {
				scopes.addAll(claimScopes);
			}
		} catch (Exception e) {
			throw new InvalidTokenException("malformed scope claim");
		}

		return new VerifiedToken(claims.getSubject(), scopes);
	}
}
