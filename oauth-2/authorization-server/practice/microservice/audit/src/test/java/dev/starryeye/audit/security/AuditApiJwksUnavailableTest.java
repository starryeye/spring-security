package dev.starryeye.audit.security;

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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
		"spring.datasource.url=jdbc:h2:mem:audit-api-jwks-unavailable;DB_CLOSE_DELAY=-1;MODE=MySQL",
		"spring.kafka.listener.auto-startup=false"   // 리스너 컨테이너만 끈다. 실제 브로커가 떠 있어도 refresh 계열 사건을 받아 적지 않게
})
class AuditApiJwksUnavailableTest {

	/**
	 * 캐시된 키가 없는 상태에서 jwks 를 받지 못하면 401 invalid_token 이 아니라 500 이 되는지를 확인한다. 키를 확보하지
	 *      못한 것은 우리 쪽 장애이지 토큰의 잘못이 아니다(token 의 AccessTokenVerifier 와 같은 원칙).
	 *
	 * 주의. my.jwk-set-uri 는 아무것도 듣지 않는 포트다. ServerSocket(0) 으로 빈 포트를 받아 바로 닫으므로, 그 사이에
	 *      다른 프로세스가 그 포트를 잡지 않는 한 연결이 거부된다.
	 *
	 * 주의. 토큰은 따로 띄운 TestJwks 의 키로 서명하고 그 jwks 서버는 바로 닫는다. 토큰 자체는 typ · iss · exp · scope 가
	 *      모두 맞는 정상 모양이라, 이 요청이 401 을 받는다면 키 확보 실패가 401 로 둔갑한 것이다. 둔갑하는 길은 둘이다 —
	 *      디코더가 그 실패를 invalid_token 으로 내는 것, 그리고 500 을 만들러 가는 /error 디스패치가 보안 설정에 거절되는
	 *      것(SecurityConfig 의 ERROR 디스패치 문단). 상태 단언은 둘을 다 막고, WWW-Authenticate 단언은 앞의 것을 따로
	 *      가리킨다.
	 */

	private static final String PATH = "/audit/users/user-sub-0001/refresh-token-families";
	private static final String ISSUER = "http://localhost:9000";

	private static final int UNUSED_PORT = unusedPort();
	private static final TestJwks SIGNER = signerWithoutJwksServer();

	@DynamicPropertySource
	static void jwks(DynamicPropertyRegistry registry) {
		registry.add("my.jwk-set-uri", () -> "http://127.0.0.1:" + UNUSED_PORT + "/oauth2/jwks");
	}

	@Autowired
	private TestRestTemplate rest;

	@Test
	@DisplayName("키를 받지 못하면 500 이고 invalid_token 으로 둔갑하지 않는다")
	void keysUnavailableIs500NotInvalidToken() {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(SIGNER.token("at+jwt", ISSUER, Instant.now().plusSeconds(300), List.of("audit.read")));

		ResponseEntity<String> response = rest.exchange(PATH, HttpMethod.GET, new HttpEntity<>(headers), String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
		assertThat(response.getHeaders().getOrEmpty(HttpHeaders.WWW_AUTHENTICATE))
				.noneMatch(value -> value.contains("invalid_token"));
	}

	private static int unusedPort() {
		try (ServerSocket socket = new ServerSocket(0)) {
			return socket.getLocalPort();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static TestJwks signerWithoutJwksServer() {
		TestJwks signer = TestJwks.start();
		signer.close();
		return signer;
	}
}
