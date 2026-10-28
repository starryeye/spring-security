package dev.starryeye.audit.security;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.datasource.url=jdbc:h2:mem:audit-api-protection;DB_CLOSE_DELAY=-1;MODE=MySQL",
		"spring.kafka.listener.auto-startup=false"   // 리스너 컨테이너만 끈다. 실제 브로커가 떠 있어도 refresh 계열 사건을 받아 적지 않게
})
class AuditApiProtectionTest {

	/**
	 * 조회 API 가 이 인가 서버의 access token(typ at+jwt, iss, exp, scope audit.read)만 받고, 나머지를 RFC 6750 의
	 *      응답으로 거절하는지를 실제 서명한 토큰과 실제 jwks 호출로 확인한다.
	 *
	 * 주의. 토큰은 TestJwks 가 서명하고 그 공개키를 TestJwks 의 jwks 서버가 내준다. my.jwk-set-uri 를 그 서버로 바꾸는
	 *      것 말고는 SecurityConfig 의 디코더를 그대로 쓴다. iss 는 테스트 yml 의 my.issuer 와 같은 값이다.
	 *
	 * 주의. 같은 키로 typ 만 다른 토큰을 만든다. signing 은 같은 키로 access token(at+jwt) · id token(JWT) · logout
	 *      token(logout+jwt)을 서명하므로, typ 이 셋을 가르는 유일한 표식이다(signing 의 SignController 참고).
	 *
	 * 주의. 만료 토큰은 exp 를 120초 전으로 둔다. JwtTimestampValidator 의 코드상 기본 시계 오차 허용은 60초라 그보다
	 *      덜 지난 토큰은 아직 받는다.
	 */

	private static final String PATH = "/audit/users/user-sub-0001/refresh-token-families";
	private static final String ISSUER = "http://localhost:9000";

	private static final TestJwks JWKS = TestJwks.start();

	@DynamicPropertySource
	static void jwks(DynamicPropertyRegistry registry) {
		registry.add("my.jwk-set-uri", JWKS::jwksUri);
	}

	@AfterAll
	static void stopJwks() {
		JWKS.close();
	}

	@Autowired
	private TestRestTemplate rest;

	@Test
	@DisplayName("토큰이 없으면 401 이고 WWW-Authenticate 는 Bearer 다")
	void noTokenIs401WithABearerChallenge() {
		ResponseEntity<String> response = rest.getForEntity(PATH, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
	}

	@Test
	@DisplayName("audit.read 를 가진 at+jwt access token 이면 200 이다")
	void operatorAccessTokenIs200() {
		ResponseEntity<String> response = get(JWKS.token("at+jwt", ISSUER, Instant.now().plusSeconds(300), List.of("audit.read")));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@ParameterizedTest
	@ValueSource(strings = {"JWT", "logout+jwt"})
	@DisplayName("같은 키로 서명한 다른 종류의 토큰(typ 만 다르다)은 401 invalid_token 이다")
	void otherTokenTypesAre401(String typ) {
		ResponseEntity<String> response = get(JWKS.token(typ, ISSUER, Instant.now().plusSeconds(300), List.of("audit.read")));

		assertInvalidToken(response);
	}

	@Test
	@DisplayName("typ 이 없는 토큰은 401 invalid_token 이다")
	void tokenWithoutTypIs401() {
		ResponseEntity<String> response = get(JWKS.token(null, ISSUER, Instant.now().plusSeconds(300), List.of("audit.read")));

		assertInvalidToken(response);
	}

	@Test
	@DisplayName("iss 가 다르면 401 invalid_token 이다")
	void wrongIssuerIs401() {
		ResponseEntity<String> response = get(JWKS.token("at+jwt", "http://evil.example", Instant.now().plusSeconds(300), List.of("audit.read")));

		assertInvalidToken(response);
	}

	@Test
	@DisplayName("시계 오차 허용(60초)보다 더 지난 토큰은 401 invalid_token 이다")
	void tokenExpiredBeyondClockSkewIs401() {
		ResponseEntity<String> response = get(JWKS.token("at+jwt", ISSUER, Instant.now().minusSeconds(120), List.of("audit.read")));

		assertInvalidToken(response);
	}

	@Test
	@DisplayName("audit.read 가 없는 access token 은 403 insufficient_scope 다")
	void accessTokenWithoutAuditReadIs403() {
		ResponseEntity<String> response = get(JWKS.token("at+jwt", ISSUER, Instant.now().plusSeconds(300), List.of("introspect")));

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).contains("error=\"insufficient_scope\"");
	}

	private ResponseEntity<String> get(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return rest.exchange(PATH, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}

	private static void assertInvalidToken(ResponseEntity<String> response) {
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).contains("error=\"invalid_token\"");
	}
}
