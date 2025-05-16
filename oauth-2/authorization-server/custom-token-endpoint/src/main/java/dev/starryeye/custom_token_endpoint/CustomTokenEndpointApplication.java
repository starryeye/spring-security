package dev.starryeye.custom_token_endpoint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomTokenEndpointApplication {

	public static void main(String[] args) {
		SpringApplication.run(CustomTokenEndpointApplication.class, args);
	}

	/**
	 * access token 을 발행하는 endpoint 에 대해 알아본다.
	 *
	 * 주요 클래스
	 * OAuth2TokenEndpointConfigurer
	 * OAuth2TokenEndpointFilter
	 * 		DelegatingAuthenticationConverter
	 * 			OAuth2AuthorizationCodeAuthenticationConverter
	 * 				OAuth2AuthorizationCodeAuthenticationToken
	 * 			OAuth2RefreshTokenAuthenticationConverter
	 * 				OAuth2RefreshTokenAuthenticationToken
	 * 			OAuth2ClientCredentialsAuthenticationConverter
	 * 				OAuth2ClientCredentialsAuthenticationToken
	 * 		ProviderManager(AuthenticationManager)
	 * 			OAuth2AuthorizationCodeAuthenticationProvider
	 * 			OAuth2RefreshTokenAuthenticationProvider
	 * 			OAuth2ClientCredentialsAuthenticationProvider
	 * 			OAuth2AccessTokenAuthenticationToken(인증 객체)
	 * 		AuthenticationSuccessHandler
	 * 		AuthenticationFailureHandler
	 */

}
