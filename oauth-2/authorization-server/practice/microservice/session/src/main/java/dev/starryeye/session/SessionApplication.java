package dev.starryeye.session;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SessionApplication {

	/**
	 * OP 세션과 RP 의 대응을 소유하고, 로그아웃 시 각 RP 에게 logout token 을 보낸다.
	 *      (OIDC Back-Channel Logout 1.0)
	 */

	public static void main(String[] args) {
		SpringApplication.run(SessionApplication.class, args);
	}
}
