package dev.starryeye.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthApplication {

	/**
	 * front-channel 을 담당하는 서비스이다.
	 *      로그인(사용자 인증은 user-directory 에 위임)과 authorize(code 발급)를 처리한다.
	 *      세션은 Redis 로 외부화하여 다중 인스턴스에서 공유한다.
	 */

	public static void main(String[] args) {
		SpringApplication.run(AuthApplication.class, args);
	}
}
