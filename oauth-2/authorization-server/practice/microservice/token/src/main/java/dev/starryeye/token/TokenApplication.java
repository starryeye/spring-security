package dev.starryeye.token;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TokenApplication {

	/**
	 * back-channel 을 담당하는 서비스이다.
	 *      code 를 access token 으로 교환하고(/oauth2/token), 표준 claim 을 구성해 signing 에 서명을 위임한다.
	 *      jwks 는 signing 이 소유하며 이 서비스는 프록시로 노출한다.
	 */

	public static void main(String[] args) {
		SpringApplication.run(TokenApplication.class, args);
	}
}
