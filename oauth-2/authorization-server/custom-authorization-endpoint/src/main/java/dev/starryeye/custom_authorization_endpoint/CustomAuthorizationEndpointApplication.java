package dev.starryeye.custom_authorization_endpoint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomAuthorizationEndpointApplication {

	public static void main(String[] args) {
		SpringApplication.run(CustomAuthorizationEndpointApplication.class, args);
	}

}

/**
 * "/oauth2/authorize" 요청을 하고 로그인 페이지가 응답 되는 flow 를 정리해본다.
 *
 * OAuth2AuthorizationEndpointFilter
 * 		"/oauth2/authorize" path 요청이면 처리
 * 		OAuth2AuthorizationCodeRequestAuthenticationConverter
 * 			요청데이터에 필수 값 존재 여부를 검증
 * 			요청데이터를 바탕으로 미 인증 객체(OAuth2AuthorizationCodeRequestAuthenticationToken) 생성
 * 				참고.. 쿠키 세션, SecurityContextHolder 로 기 인증이면 인증된 객체로 생성됨
 * 		ProviderManager(AuthenticationManager)
 * 			OAuth2AuthorizationCodeRequestAuthenticationProvider
 * 				RegisteredClientRepository 로 인증 객체의 client id 값 기반 RegisteredClient 조회
 * 				요청 데이터 기반 인증 객체와 RegisteredClient 의 값을 서로 비교하여 검증
 * 				현재 인증 객체의 인증 여부를 조건으로 미 인증이라면, provider 에서 return
 * 		return 된 인증 객체의 인증 여부를 조건으로 미 인증이라면, doFilter 로 미 인증 객체를 넘겨서 진행.
 * 미 인증 되었으므로.. 인증 유도 페이지 응답 (로그인 페이지)
 */
