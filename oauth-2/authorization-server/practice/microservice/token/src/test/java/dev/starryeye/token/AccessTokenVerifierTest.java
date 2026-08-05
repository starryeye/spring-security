package dev.starryeye.token;

import com.nimbusds.jose.JOSEObjectType;
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

	// 기존 호출부는 typ 을 신경 쓰지 않으므로, 검증 대상인 at+jwt 를 기본값으로 채운다.
	private String sign(RSAKey signingKey, String issuer, Date expiration, String sub, List<String> scope) throws Exception {
		return sign(signingKey, issuer, expiration, sub, scope, "at+jwt");
	}

	private String sign(RSAKey signingKey, String issuer, Date expiration, String sub, List<String> scope, String typ) throws Exception {
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.subject(sub)
				.issuer(issuer)
				.audience("my-client")
				.expirationTime(expiration)
				.issueTime(Date.from(Instant.now().minusSeconds(60)))
				.claim("scope", scope)
				.build();
		JWSHeader.Builder headerBuilder = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID());
		if (typ != null) {
			headerBuilder.type(new JOSEObjectType(typ));
		}
		SignedJWT signedJWT = new SignedJWT(headerBuilder.build(), claims);
		signedJWT.sign(new RSASSASigner(signingKey));
		return signedJWT.serialize();
	}

	// typ 만 바꿔 가며 검증기의 typ 강제를 테스트하기 위한 헬퍼. 나머지 claim 은 verifiesValidToken 과 같은 정상값으로 고정한다.
	private String signWithTyp(String typ) throws Exception {
		return sign(rsaKey, ISSUER, futureExpiration(), "user-sub-0001", List.of("openid"), typ);
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
		assertThat(verified.clientId()).isEqualTo("my-client");
		assertThat(verified.exp()).isPositive();
		assertThat(verified.iat()).isPositive();
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

	// typ 이 at+jwt 가 아니면 거부한다. 같은 키로 서명된 id token·logout token 이 access token 으로
	// 통하는 것을 막는 구조적 방어이며, scope claim 부재 같은 우연한 결손에 의존하지 않는다.
	@Test
	void rejectsTokenWhoseTypIsNotAccessToken() throws Exception {
		String idTokenLike = signWithTyp("JWT");

		assertThatThrownBy(() -> accessTokenVerifier.verify(idTokenLike))
				.isInstanceOf(AccessTokenVerifier.InvalidTokenException.class);
	}

	@Test
	void rejectsLogoutTokenPresentedAsAccessToken() throws Exception {
		String logoutTokenLike = signWithTyp("logout+jwt");

		assertThatThrownBy(() -> accessTokenVerifier.verify(logoutTokenLike))
				.isInstanceOf(AccessTokenVerifier.InvalidTokenException.class);
	}

	// typ 헤더가 아예 없는(null) 토큰 -> 슬라이스 5 이전에 발급된 access token 이 전부 이 모양이다.
	// 헬퍼의 if (typ != null) 분기가 이 테스트 없이는 죽은 코드였다.
	@Test
	void rejectsTokenWithMissingTyp() throws Exception {
		String token = sign(rsaKey, ISSUER, futureExpiration(), "user-sub-0001", List.of("openid"), null);

		assertThatThrownBy(() -> accessTokenVerifier.verify(token))
				.isInstanceOf(AccessTokenVerifier.InvalidTokenException.class)
				.hasMessage("unexpected token type");
	}

	@Test
	void acceptsTokenWithAccessTokenTyp() throws Exception {
		String accessToken = signWithTyp("at+jwt");

		assertThat(accessTokenVerifier.verify(accessToken).sub()).isEqualTo("user-sub-0001");
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
