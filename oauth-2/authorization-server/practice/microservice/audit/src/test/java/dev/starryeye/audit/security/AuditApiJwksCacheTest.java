package dev.starryeye.audit.security;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
		"spring.datasource.url=jdbc:h2:mem:audit-api-jwks-cache;DB_CLOSE_DELAY=-1;MODE=MySQL",
		"spring.kafka.listener.auto-startup=false"   // 리스너 컨테이너만 끈다. 실제 브로커가 떠 있어도 refresh 계열 사건을 받아 적지 않게
})
class AuditApiJwksCacheTest {

	/**
	 * 키를 한 번 받은 뒤에는 jwks 서버가 내려가도 같은 키로 검증이 계속되는지를 확인한다.
	 *
	 * 주의. 이 테스트가 보이는 것은 "키를 받은 직후(몇 초 안)에 jwks 가 내려가도 200"뿐이다. 그 캐시가 얼마나 버티는지
	 *      (Nimbus 9.37.3 DefaultJWKSetCache 의 코드상 기본값 — 수명 15분, 갱신 시점 5분)는 SecurityConfig 의 주석에 코드
	 *      읽기로 적혀 있고, 이 테스트는 그 시간을 재지 않는다.
	 *
	 * 주의. 이 클래스는 다른 security 테스트와 프로퍼티(my.jwk-set-uri)가 달라 컨텍스트가 따로 뜬다. 그래서 다른 클래스가
	 *      채운 캐시를 물려받지 않는다 — 첫 요청이 이 클래스의 jwks 서버에서 키를 받는다.
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
	@DisplayName("키를 받은 뒤 jwks 서버가 내려가도 같은 토큰은 200 이다")
	void cachedKeysKeepWorkingWhileJwksIsDown() {
		String token = JWKS.token("at+jwt", ISSUER, Instant.now().plusSeconds(300), List.of("audit.read"));

		assertThat(get(token).getStatusCode()).isEqualTo(HttpStatus.OK);

		JWKS.close();

		assertThat(get(token).getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	private ResponseEntity<String> get(String token) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(token);
		return rest.exchange(PATH, HttpMethod.GET, new HttpEntity<>(headers), String.class);
	}
}
