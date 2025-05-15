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
 * 				참고.. 쿠키 세션, SecurityContextHolder 로 기 인증이면 인증된 principal 으로 생성됨
 * 		ProviderManager(AuthenticationManager)
 * 			OAuth2AuthorizationCodeRequestAuthenticationProvider
 * 				RegisteredClientRepository 로 인증 객체의 client id 값 기반 RegisteredClient 조회
 * 				요청 데이터 기반 인증 객체와 RegisteredClient 의 값을 서로 비교하여 검증
 * 				현재 인증 객체 principal 의 인증 여부를 조건으로 미 인증이라면, provider 에서 return
 * 		return 된 인증 객체의 인증 여부를 조건으로 미 인증이라면, doFilter 로 미 인증 객체를 넘겨서 진행.
 * 미 인증 되었으므로.. 인증 유도 페이지 응답 (로그인 페이지)
 */

/**
 * 로그인 페이지에서 사용자가 ID/PASSWORD 를 입력하여 form 로그인 요청을 했을 때의 flow 를 정리해본다.
 *
 * UsernamePasswordAuthenticationFilter
 * 		"/login" path 요청이면 처리
 * 		UsernamePasswordAuthenticationToken(미 인증) 인증 객체 생성
 * 		ProviderManager(AuthenticationManager) 에서 인증 처리 (내부 과정 생략)
 * 		인증된 인증 객체를 return 받고 AbstractAuthenticationProcessingFilter::doFilter 에서 successfulAuthentication 를 수행
 * 			SavedRequestAwareAuthenticationSuccessHandler::onAuthenticationSuccess
 * 				세션에 적재되어있던 SavedRequest 에서 로그인 페이지로 오기 직전의 요청 url 추출
 * 				로그인 페이지로 오기 직전의 요청 url 로 redirect 보낸다.
 * 					-> "/oauth2/authorize" 요청이다.
 *
 * redirect 된 요청은 최초의 요청과 동일하며 달라진 점은 쿠키 세션으로 authorization server 에서 사용자가 인증된 상태라는 것..
 * OAuth2AuthorizationEndpointFilter
 * 		"/oauth2/authorize" path 요청이면 처리
 * 		OAuth2AuthorizationCodeRequestAuthenticationConverter
 * 			요청데이터에 필수 값 존재 여부를 검증
 * 			SecurityContextHolder 를 참조하여 미 인증 객체(OAuth2AuthorizationCodeRequestAuthenticationToken) 생성 (principal authenticated)
 * 		ProviderManager(AuthenticationManager)
 * 			OAuth2AuthorizationCodeRequestAuthenticationProvider
 * 				RegisteredClientRepository 로 인증 객체의 client id 값 기반 RegisteredClient 조회
 * 				요청 데이터 기반 인증 객체와 RegisteredClient 의 값을 서로 비교하여 검증
 * 				현재 인증 객체 principal 의 인증 여부를 조건으로 인증 여부 체크 (인증)
 * 				OAuth2AuthorizationRequest 생성
 * 				OAuth2AuthorizationConsent 생성
 * 					OAuth2AuthorizationConsentService 에서 과거에 해당 사용자가 동의를 한 적이 있는지 조회
 * 				OAuth2Authorization 생성 및 저장
 * 				OAuth2AuthorizationConsentAuthenticationToken 생성하여 리턴
 * 		sendAuthorizationConsent()
 * 			사용자 지정 consent page or default consent page 를 사용자에게 응답.
 */

/**
 * 사용자가 consent 동의 페이지에서 동의를 요청한 경우의 flow 를 정리 해본다.
 * OAuth2AuthorizationEndpointFilter
 * 		"/oauth2/authorize" path 요청이면 처리
 * 		OAuth2AuthorizationCodeRequestAuthenticationConverter
 * 			요청데이터에 필수 값 존재 여부를 검증
 * 			SecurityContextHolder 를 참조하여 기 인증 객체(OAuth2AuthorizationConsentAuthenticationToken) 생성
 * 		ProviderManager(AuthenticationManager)
 * 			OAuth2AuthorizationConsentAuthenticationProvider
 * 				OAuth2AuthorizationService 에서 OAuth2Authorization 조회 (by state)
 * 				RegisteredClientRepository 에서 RegisteredClient 조회	(by client id)
 * 				OAuth2Authorization 에서 OAuth2AuthorizationRequest 참조
 * 				허용되는 scope 전체 집합(consent)에 사용자가 허용한 scope 가 포함되는지 체크
 * 				OAuth2AuthorizationConsentService 에서 OAuth2AuthorizationConsent 조회 (by client id, principal)
 * 				OAuth2AuthorizationConsent 에 사용자 동의 항목을 바탕으로 authorities 매핑 후 OAuth2AuthorizationConsentService 로 저장
 * 				OAuth2TokenContext 생성
 * 				OAuth2AuthorizationCode 생성	(OAuth2TokenContext 참조)
 * 				OAuth2Authorization 에 생성한 OAuth2AuthorizationCode, 사용자가 동의한 scope 를 업데이트
 * 				OAuth2AuthorizationService 에 OAuth2Authorization 저장(업데이트)
 * 				OAuth2AuthorizationRequest 에서 client redirect url 조회
 * 				OAuth2AuthorizationCodeRequestAuthenticationToken 생성 및 return
 * 		sendAuthorizationResponse()
 * 				client redirect url 로 code 를 query parameter 를 더하여 redirect
 *
 */
