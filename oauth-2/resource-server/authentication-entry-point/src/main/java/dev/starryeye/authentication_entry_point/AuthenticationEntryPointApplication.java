package dev.starryeye.authentication_entry_point;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthenticationEntryPointApplication {

	/**
	 * 기본 개념
	 * AuthenticationEntryPoint ..
	 * 		사용자가 어떤 서버로 접근할 때, 기본적으로 security 에서는 인증이 필요하다.
	 * 		인증을 받지 않은 상태라면 서버는 해당 자원에 접근을 하지 못하게 막는다.
	 * 		동시에 사용자로 하여금 인증을 받을 수 있게 해줘야한다.
	 * 			로그인 페이지로 이동시킴..
	 * 		AuthenticationEntryPoint 는 인증을 받을 수 있게 인증의 진입점으로 연결 시키는 역할을 한다.
	 *
	 * AuthenticationEntryPoint 의 구현체는 여러가지이다.
	 * 		여러개의 AuthenticationEntryPoint 구현체가 존재할때.. 선택되는 기준은
	 * 			1. 요청된 url 의 인증 방법에 따라 결정
	 * 			2. 현재 프로젝트에서 사용되고 있는 인증 방법에 따라 결정 (혼합되어 사용될 경우 우선순위 존재)
	 *		formLogin 설정시, LoginUrlAuthenticationEntryPoint
	 *		httpBasic 설정시, BasicAuthenticationEntryPoint
	 *		oauth2 resource server 일 경우, BearerTokenAuthenticationEntryPoint
	 *
	 * 참고
	 * 개발자가 설정으로 authenticationEntryPoint 를 설정할 수 있는데 이경우에는 개발자가 등록한게 우선이다.
	 */

	/**
	 * oauth2-resource-server 의존성 에서의 AuthenticationEntryPoint 에 대해 알아보자.
	 *
	 * OAuth2ResourceServerConfigurer 에 따라 BearerTokenAuthenticationEntryPoint 가 사용되고 있음을 볼 수 있다.
	 * 		OAuth2ResourceServerConfigurer::registerDefaultEntryPoint 에서
	 * 			ExceptionHandlingConfigurer 를 사용하여 BearerTokenAuthenticationEntryPoint 를 기본 AuthenticationEntryPoint 로 설정하고 있다.
	 * 				ExceptionHandlingConfigurer 는..
	 * 					인증/인가 예외 발생시 처리를 관장하는 ExceptionTranslationFilter 에서, 해당 BearerTokenAuthenticationEntryPoint 를 사용하도록 함.
	 *
	 */

	public static void main(String[] args) {
		SpringApplication.run(AuthenticationEntryPointApplication.class, args);
	}

}
