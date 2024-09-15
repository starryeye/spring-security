package dev.starryeye.custom_authenticate_authentication_manager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomAuthenticateAuthenticationManagerApplication {

	/**
	 * AuthenticationManager 를 다뤄보자.
	 *
	 * case_1, case_2 에 존재하는 SecurityConfig 의 @Configuration 어노테이션을
	 * 각각 활성화 비활성화 하면서 실행
	 */

	public static void main(String[] args) {
		SpringApplication.run(CustomAuthenticateAuthenticationManagerApplication.class, args);
	}

}
