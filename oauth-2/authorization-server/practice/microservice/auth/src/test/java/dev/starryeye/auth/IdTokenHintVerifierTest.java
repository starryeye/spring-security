package dev.starryeye.auth;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.starryeye.auth.client.SigningJwksClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdTokenHintVerifierTest {

	private static final String ISSUER = "http://localhost:9000";

	private RSAKey key;
	private IdTokenHintVerifier verifier;

	@BeforeEach
	void setUp() throws Exception {
		key = new RSAKeyGenerator(2048).keyID("test-kid").generate();
		SigningJwksClient jwksClient = mock(SigningJwksClient.class);
		when(jwksClient.jwks()).thenReturn(new com.nimbusds.jose.jwk.JWKSet(key.toPublicJWK()).toJSONObject());
		verifier = new IdTokenHintVerifier(jwksClient, ISSUER);
	}

	private String sign(String aud, String issuer, Instant expiration) throws Exception {
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.issuer(issuer)
				.subject("user-sub-0001")
				.audience(aud)
				.expirationTime(Date.from(expiration))
				.build();
		SignedJWT jwt = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}

	@Test
	void returnsAudienceOfAValidHint() throws Exception {
		assertThat(verifier.verify(sign("demo-rp", ISSUER, Instant.now().plusSeconds(300))))
				.isEqualTo("demo-rp");
	}

	// 로그아웃 시점에 id token 이 만료돼 있는 것은 정상이다. 만료를 이유로 거부하면 정당한 로그아웃이 막힌다.
	// 이 저장소에서 만료를 일부러 무시하는 유일한 검증이다.
	@Test
	void acceptsAnExpiredHint() throws Exception {
		assertThat(verifier.verify(sign("demo-rp", ISSUER, Instant.now().minusSeconds(3600))))
				.isEqualTo("demo-rp");
	}

	@Test
	void rejectsAHintSignedByAnotherKey() throws Exception {
		RSAKey attacker = new RSAKeyGenerator(2048).keyID("test-kid").generate();
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.issuer(ISSUER).subject("user-sub-0001").audience("demo-rp").build();
		SignedJWT forged = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test-kid").build(), claims);
		forged.sign(new RSASSASigner(attacker));

		assertThatThrownBy(() -> verifier.verify(forged.serialize()))
				.isInstanceOf(IdTokenHintVerifier.InvalidHintException.class);
	}

	@Test
	void rejectsAHintFromAnotherIssuer() throws Exception {
		assertThatThrownBy(() -> verifier.verify(sign("demo-rp", "http://evil.example", Instant.now())))
				.isInstanceOf(IdTokenHintVerifier.InvalidHintException.class);
	}

	@Test
	void rejectsGarbage() {
		assertThatThrownBy(() -> verifier.verify("not-a-jwt"))
				.isInstanceOf(IdTokenHintVerifier.InvalidHintException.class);
	}

	// signing 장애로 키를 못 구하는 것과 힌트가 무효한 것은 다른 사건이지만, 로그아웃에서는 둘 다
	// "어디로 돌려보낼지 모른다" 로 귀결된다. 다른 예외가 새어나가면 컨트롤러가 그것을 잡지 못해
	// 로그아웃 자체가 500 으로 죽는다.
	@Test
	void rejectsHintWhenJwksIsUnavailable() throws Exception {
		SigningJwksClient failing = mock(SigningJwksClient.class);
		when(failing.jwks()).thenThrow(new RuntimeException("signing down"));

		IdTokenHintVerifier verifierWithoutJwks = new IdTokenHintVerifier(failing, ISSUER);

		assertThatThrownBy(() -> verifierWithoutJwks.verify(sign("demo-rp", ISSUER, Instant.now())))
				.isInstanceOf(IdTokenHintVerifier.InvalidHintException.class);
	}
}
