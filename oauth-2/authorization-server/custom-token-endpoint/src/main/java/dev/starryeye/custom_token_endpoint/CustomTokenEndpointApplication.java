package dev.starryeye.custom_token_endpoint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomTokenEndpointApplication {

	public static void main(String[] args) {
		SpringApplication.run(CustomTokenEndpointApplication.class, args);
	}

	/**
	 * authorization code grant 방식으로
	 * access token 을 발행하는 endpoint 처리 flow 에 대해 알아본다.
	 *
	 * OAuth2TokenEndpointFilter
	 * 		DelegatingAuthenticationConverter
	 * 			OAuth2AuthorizationCodeAuthenticationConverter
	 * 				OAuth2AuthorizationCodeAuthenticationToken 생성 (미인증 객체)
	 * 					code, grant_type, redirect_uri 은 요청데이터에서 뽑는다.
	 * 					principal 은 앞선 OAuth2ClientAuthenticationFilter 에서 인증 처리한
	 * 						client 인증 객체를 사용한다.(SecurityContextHolder 에서 뽑기)
	 * 		ProviderManager(AuthenticationManager)
	 * 			OAuth2AuthorizationCodeAuthenticationProvider
	 * 				OAuth2AuthorizationService 에서 OAuth2Authorization 를 조회한다. (by code)
	 * 				RegisteredClient(by principal, 현재 요청 데이터) 와 OAuth2AuthorizationRequest(by OAuth2Authorization) 비교
	 * 					현재 요청데이터의 client 와 code 가 발급될 당시의 client 가 같은지 확인
	 * 					현재 요청데이터의 redirect_uri 와 code 가 발급될 당시의 redirect_uri 가 같은지 확인
	 * 				code active 여부 확인 (만료여부, 한번도 사용하지 않은 코드인지..)
	 * 				DelegatingOAuth2TokenGenerator
	 * 					JwtGenerator
	 * 						JWT access token 을 생성하고 JwtEncoder 로 전자서명한다. (JwtEncoder 는 resource server 의 JwtDecoder 와 대칭됨)
	 * 						OAuth2Token 생성 후 return
	 * 				OAuth2Token 으로 OAuth2AccessToken 생성
	 * 				DelegatingOAuth2TokenGenerator, JwtGenerator 로 OAuth2Token(refresh token, id token) 을 만든다.
	 * 					OAuth2RefreshToken, OidcIdToken 생성
	 * 				OAuth2AuthorizationService 를 통해서 code 비활성화
	 * 				OAuth2AccessTokenAuthenticationToken 생성 후 return
	 * 		OAuth2AccessTokenResponseAuthenticationSuccessHandler
	 * 			OAuth2AccessTokenResponse 생성 및 최종 응답
	 * 		OAuth2ErrorAuthenticationFailureHandler
	 */

}
