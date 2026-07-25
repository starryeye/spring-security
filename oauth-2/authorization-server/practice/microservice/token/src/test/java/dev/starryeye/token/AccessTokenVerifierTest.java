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

	// 서명·issuer 는 정상이고 exp 만 과거인 토큰 -> 만료로만 거부돼야 한다.
	@Test
	void rejectsExpiredToken() throws Exception {
		String token = sign(rsaKey, ISSUER, pastExpiration(), "user-sub-0001", List.of("openid"));

		assertThatThrownBy(() -> accessTokenVerifier.verify(token))
				.isInstanceOf(AccessTokenVerifier.InvalidTokenException.class);
	}

	// 서명·exp 는 정상이고 iss 만 설정값과 다른 토큰 -> issuer 불일치로만 거부돼야 한다.
	@Test
	void rejectsIssuerMismatch() throws Exception {
		String token = sign(rsaKey, "http://wrong-issuer", futureExpiration(), "user-sub-0001", List.of("openid"));

		assertThatThrownBy(() -> accessTokenVerifier.verify(token))
				.isInstanceOf(AccessTokenVerifier.InvalidTokenException.class);
	}

	// claim(iss/exp) 은 모두 정상이지만, jwks 에 공개된 키가 아닌 다른 RSA 키로 서명한 토큰 -> 서명 검증 실패로만 거부돼야 한다.
	@Test
	void rejectsTokenSignedWithUntrustedKey() throws Exception {
		RSAKey untrustedKey = new RSAKeyGenerator(2048).keyID("test-kid").generate();
		String token = sign(untrustedKey, ISSUER, futureExpiration(), "user-sub-0001", List.of("openid"));

		assertThatThrownBy(() -> accessTokenVerifier.verify(token))
				.isInstanceOf(AccessTokenVerifier.InvalidTokenException.class);
	}

	@Test
	void rejectsMalformedToken() {
		assertThatThrownBy(() -> accessTokenVerifier.verify("not-a-jwt"))
				.isInstanceOf(AccessTokenVerifier.InvalidTokenException.class);
	}
}
