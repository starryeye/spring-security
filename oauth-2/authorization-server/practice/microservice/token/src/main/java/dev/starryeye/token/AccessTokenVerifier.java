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

import java.text.ParseException;
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
	 * 주의. "키를 확보하지 못했다"와 "토큰이 무효다"는 다른 사건이라 예외를 갈라 던진다.
	 *      jwks 조회·파싱 실패는 우리 쪽 장애이므로 그대로 전파해 500 server_error 가 되게 하고,
	 *      InvalidTokenException 으로 바꾸지 않는다. 401 invalid_token 으로 응답하면 RP 는 멀쩡한 토큰을
	 *      폐기하고 재인증을 돌리므로, signing 장애 한 번이 전 RP 의 동시 재인증으로 증폭된다.
	 *      다만 jwks 에 토큰의 kid 가 없는 경우는 키 확보 실패가 아니라 위조 kid 이므로 InvalidTokenException 이다.
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

	public VerifiedToken verify(String token) {

		SignedJWT signedJWT;
		JWTClaimsSet claims;
		try {
			signedJWT = SignedJWT.parse(token);
			claims = signedJWT.getJWTClaimsSet();
		} catch (Exception e) {
			throw new InvalidTokenException("malformed token");
		}

		// 키 확보는 검증 밖이다. 여기서 터지는 예외는 토큰의 죄가 아니므로 잡지 않고 그대로 올려보낸다.
		JWKSet jwkSet = fetchJwkSet();

		try {
			RSAKey key = (RSAKey) jwkSet.getKeyByKeyId(signedJWT.getHeader().getKeyID());
			if (key == null) {
				throw new InvalidTokenException("unknown kid"); // 우리가 공개한 적 없는 kid = 토큰 문제
			}
			if (!signedJWT.verify(new RSASSAVerifier(key.toRSAPublicKey()))) {
				throw new InvalidTokenException("signature verification failed");
			}
		} catch (InvalidTokenException e) {
			throw e;
		} catch (Exception e) {
			throw new InvalidTokenException("signature verification failed");
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

	/**
	 * signing 의 jwks 를 가져와 JWKSet 으로 만든다.
	 *      원격 호출 실패(연결 불가·5xx)는 RestClient 예외 그대로, 파싱 실패는 IllegalStateException 으로 전파한다.
	 *      둘 다 InvalidTokenException 이 아니어야 한다.
	 */
	@SuppressWarnings("unchecked")
	private JWKSet fetchJwkSet() {
		Map<String, Object> jwks = (Map<String, Object>) signingClient.jwks();
		try {
			return JWKSet.parse(jwks);
		} catch (ParseException e) {
			throw new IllegalStateException("failed to parse jwks from signing", e);
		}
	}
}
