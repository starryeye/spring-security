package dev.starryeye.audit.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * 테스트용 서명 키와 그 공개키를 내주는 jwks 서버. signing 의 /oauth2/jwks 를 대신한다.
 *
 * 주의. 토큰의 모양은 token 의 AccessTokenIssuer 를 따른다 — RS256, 헤더에 kid, claim 은 iss · sub · aud · iat · exp ·
 *      scope 이고 scope 는 JSON 배열이다. client_credentials 토큰이라 sub 와 aud 가 모두 client_id(audit-operator)다.
 *
 * 주의. iat 는 exp 보다 300초(token 의 my.access-token-ttl-seconds) 앞이다. iat 를 언제나 지금으로 두면 이미 만료된
 *      토큰은 exp 가 iat 보다 앞선다. Spring 의 Jwt 는 그런 값으로 만들어지지 않고("expiresAt must be after issuedAt")
 *      디코더는 그 실패를 401 invalid_token 으로 내므로, 만료 판정(JwtTimestampValidator)에 닿기도 전에 401 이 된다 —
 *      만료 테스트가 엉뚱한 이유로 통과한다.
 *
 * 주의. close 는 jwks 서버를 멈춘다. 그 뒤로 jwksUri 는 연결이 거부되지만 이 객체의 키로 서명은 계속할 수 있다 —
 *      "키를 받지 못하는 상태에서 온 정상 모양의 토큰"을 만들 때 쓴다.
 */
final class TestJwks implements AutoCloseable {

	static final String KID = "test-kid";
	private static final long ACCESS_TOKEN_TTL_SECONDS = 300;

	private final RSAKey key;
	private final HttpServer server;
	private boolean closed;

	private TestJwks(RSAKey key, HttpServer server) {
		this.key = key;
		this.server = server;
	}

	static TestJwks start() {
		try {
			RSAKey key = new RSAKeyGenerator(2048).keyID(KID).generate();
			byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);

			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/oauth2/jwks", exchange -> {
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, body.length);
				try (OutputStream out = exchange.getResponseBody()) {
					out.write(body);
				}
			});
			server.start();
			return new TestJwks(key, server);
		} catch (JOSEException e) {
			throw new IllegalStateException(e);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	String jwksUri() {
		return "http://127.0.0.1:" + server.getAddress().getPort() + "/oauth2/jwks";
	}

	/**
	 * @param typ null 이면 헤더에 typ 이 없다
	 */
	String token(String typ, String issuer, Instant expiresAt, List<String> scope) {
		JWSHeader.Builder header = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KID);
		if (typ != null) {
			header.type(new JOSEObjectType(typ));
		}
		JWTClaimsSet claims = new JWTClaimsSet.Builder()
				.issuer(issuer)
				.subject("audit-operator")
				.audience("audit-operator")
				.issueTime(Date.from(expiresAt.minusSeconds(ACCESS_TOKEN_TTL_SECONDS)))
				.expirationTime(Date.from(expiresAt))
				.claim("scope", scope)
				.build();
		try {
			SignedJWT jwt = new SignedJWT(header.build(), claims);
			jwt.sign(new RSASSASigner(key));
			return jwt.serialize();
		} catch (JOSEException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * 두 번 불러도 된다. 테스트 안에서 닫고 @AfterAll 에서 다시 닫는 경우가 있다.
	 */
	@Override
	public synchronized void close() {
		if (!closed) {
			server.stop(0);
			closed = true;
		}
	}
}
