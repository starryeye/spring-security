package dev.starryeye.default_security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DefaultSecurityApplication {

	/**
	 * implementation 'org.springframework.boot:spring-boot-starter-security'
	 * 의존성을 추가 시키면 spring boot auto-configuration 으로 부터 default security 가 작동하도록 설정된다.
	 *
	 * default security
	 * 1. 모든 요청에 대해 인증되어야 자원 접근이 가능하다.
	 * 2. 인증 방식은 formLogin, httpBasic 방식 2 가지가 제공된다. (localhost:8080 으로 chrome 접근하면 formLogin 창이 뜬다.)
	 * 3. 인증 승인을 위한 기본 계정이 부팅시 제공된다.
	 * 		username : user, password : 부팅 시 로그로 출력
	 *
	 * 관련 클래스 및 메서드
	 * 1번, 2번 관련
	 * 		SpringBootWebSecurityConfiguration.SecurityFilterChainConfiguration::defaultSecurityFilterChain
	 * 3번 관련
	 * 		UserDetailsServiceAutoConfiguration::inMemoryUserDetailsManager
	 * 			SpringProperties 를 이용
	 *
	 *
	 *
	 */

	public static void main(String[] args) {
		SpringApplication.run(DefaultSecurityApplication.class, args);
	}

}
