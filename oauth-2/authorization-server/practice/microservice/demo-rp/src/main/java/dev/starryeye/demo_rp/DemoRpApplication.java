package dev.starryeye.demo_rp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DemoRpApplication {

	/**
	 * 이 인가 서버의 back-channel logout 을 검증하는 RP 다.
	 *      검증자가 우리 코드가 아니라 Spring Security 구현이므로, 스펙을 잘못 읽으면 실제로 실패한다.
	 */

	public static void main(String[] args) {
		SpringApplication.run(DemoRpApplication.class, args);
	}
}
