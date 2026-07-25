package dev.starryeye.token;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.starryeye.token.client.SigningClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class AccessTokenVerifierTest {

	// application.yml 의 my.issuer 와 같은 값이어야 issuer 검증을 통과한다.
	private static final String ISSUER = "http://localhost:9000";

	@Autowired
	AccessTokenVerifier accessTokenVerifier;

	@MockitoBean
	SigningClient signingClient;

	RSAKey rsaKey;

	@BeforeEach
	void setUp() throws Exception {
		rsaKey = new RSAKeyGenerator(2048).keyID("test-kid").generate();
		Map<String, Object> jwks = new JWKSet(rsaKey.toPublicJWK()).toJSONObject();
		doReturn(jwks).when(signingClient).jwks();
	}

	private String sign(RSAKey signingKey, String issuer, Date expiration, String sub, List<String> scope) throws Exception {
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.subject(sub)
				.issuer(issuer)
				.expirationTime(expiration)
				.claim("scope", scope)
				.build();
		SignedJWT signedJWT = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
				claims);
		signedJWT.sign(new RSASSASigner(signingKey));
		return signedJWT.serialize();
	}

	private Date futureExpiration() {
		return Date.from(Instant.now().plusSeconds(300));
	}

	private Date pastExpiration() {
		return Date.from(Instant.now().minusSeconds(300));
	}

	@Test
	void verifiesValidToken() throws Exception {
		String token = sign(rsaKey, ISSUER, futureExpiration(), "user-sub-0001", List.of("openid", "profile"));

		AccessTokenVerifier.VerifiedToken verified = accessTokenVerifier.verify(token);

		assertThat(verified.sub()).isEqualTo("user-sub-0001");
		assertThat(verified.scopes()).containsExactly("openid", "profile");
	}

	// 서명·issuer 는 정상이고 exp 만 과거인 토큰 -> 만료로만 거부돼야 한다. (거부 사유까지 단언해 다른 이유로 통과하는 것을 막는다)
	@Test
	void rejectsExpiredToken() throws Exception {
		String token = sign(rsaKey, ISSUER, pastExpiration(), "user-sub-0001", List.of("openid"));

		assertThatThrownBy(() -> accessTokenVerifier.verify(token))
				.isInstanceOf(AccessTokenVerifier.InvalidTokenException.class)
				.hasMessage("token expired");
	}

	// 서명·exp 는 정상이고 iss 만 설정값과 다른 토큰 -> issuer 불일치로만 거부돼야 한다.
	@Test
	void rejectsIssuerMismatch() throws Exception {
		String token = sign(rsaKey, "http://wrong-issuer", futureExpiration(), "user-sub-0001", List.of("openid"));

		assertThatThrownBy(() -> accessTokenVerifier.verify(token))
				.isInstanceOf(AccessTokenVerifier.InvalidTokenException.class)
				.hasMessage("issuer mismatch");
	}

	// claim(iss/exp) 은 모두 정상이지만, jwks 에 공개된 키가 아닌 다른 RSA 키로 서명한 토큰 -> 서명 검증 실패로만 거부돼야 한다.
	// kid 는 jwks 에 있는 값 그대로라 "키를 못 찾음"이 아니라 "서명이 안 맞음"으로 걸려야 한다.
	@Test
	void rejectsTokenSignedWithUntrustedKey() throws Exception {
		RSAKey untrustedKey = new RSAKeyGenerator(2048).keyID("test-kid").generate();
		String token = sign(untrustedKey, ISSUER, futureExpiration(), "user-sub-0001", List.of("openid"));

		assertThatThrownBy(() -> accessTokenVerifier.verify(token))
				.isInstanceOf(AccessTokenVerifier.InvalidTokenException.class)
				.hasMessage("signature verification failed");
	}

	// jwks 에 없는 kid -> 우리가 공개한 적 없는 키를 가리키므로 토큰 문제(InvalidTokenException)다.
	@Test
	void rejectsTokenWithUnknownKid() throws Exception {
		RSAKey otherKey = new RSAKeyGenerator(2048).keyID("not-published-kid").generate();
		String token = sign(otherKey, ISSUER, futureExpiration(), "user-sub-0001", List.of("openid"));

		assertThatThrownBy(() -> accessTokenVerifier.verify(token))
				.isInstanceOf(AccessTokenVerifier.InvalidTokenException.class)
				.hasMessage("unknown kid");
	}

	@Test
	void rejectsMalformedToken() {
		assertThatThrownBy(() -> accessTokenVerifier.verify("not-a-jwt"))
				.isInstanceOf(AccessTokenVerifier.InvalidTokenException.class)
				.hasMessage("malformed token");
	}

	// signing 이 죽어 jwks 를 못 가져오는 것은 "토큰이 무효"가 아니라 우리 쪽 장애다.
	// InvalidTokenException 으로 바꿔 던지면 호출부가 401 invalid_token 을 내고 RP 가 멀쩡한 토큰을 폐기한다.
	@Test
	void propagatesJwksFailureInsteadOfInvalidToken() throws Exception {
		String token = sign(rsaKey, ISSUER, futureExpiration(), "user-sub-0001", List.of("openid"));
		doThrow(new IllegalStateException("signing is down")).when(signingClient).jwks();

		assertThatThrownBy(() -> accessTokenVerifier.verify(token))
				.isNotInstanceOf(AccessTokenVerifier.InvalidTokenException.class)
				.hasMessage("signing is down");
	}
}
