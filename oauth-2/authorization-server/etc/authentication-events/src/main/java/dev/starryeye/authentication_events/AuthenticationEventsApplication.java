package dev.starryeye.authentication_events;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthenticationEventsApplication {

	/**
	 * spring security 의 인증 이벤트(Authentication Events)로 감사(audit) 로그를 남겨본다.
	 *      운영에서 로그인 성공/실패 기록, 실패 반복 감지(brute force), 알림 등의 기반이 되는 요소이다.
	 *
	 * 이벤트 발행 구조..
	 *      spring boot 가 DefaultAuthenticationEventPublisher 를 빈으로 자동 등록해주고..
	 *      security 초기화 시 AuthenticationManager(ProviderManager) 에 연결되어..
	 *          인증 성공 : AuthenticationSuccessEvent 발행
	 *          인증 실패 : 예외 타입별로 매핑된 AbstractAuthenticationFailureEvent 하위 이벤트 발행
	 *              (예. BadCredentialsException -> AuthenticationFailureBadCredentialsEvent)
	 *      개발자는 @EventListener 로 받기만 하면 된다. (AuthenticationEventsListener 참고)
	 *
	 * 확인 포인트
	 *      1. form login 실패(틀린 비밀번호) -> AuthenticationFailureBadCredentialsEvent 가 수신된다.
	 *      2. form login 성공 -> AuthenticationSuccessEvent 가 수신된다.
	 *      3. authorization server 의 client 인증(OAuth2ClientAuthenticationFilter, "/oauth2/token" 의 client_secret_basic)도
	 *          이벤트가 발행되는지 관찰한다. (아래 관찰 결과)
	 *
	 * 관찰 결과 (전부 실행해서 확인한 것)
	 *      form login 실패 : AuthenticationFailureBadCredentialsEvent (authentication=UsernamePasswordAuthenticationToken)
	 *      form login 성공 : AuthenticationSuccessEvent 1건 (authentication=UsernamePasswordAuthenticationToken)
	 *      client 인증 성공 : AuthenticationSuccessEvent (authentication=OAuth2ClientAuthenticationToken)
	 *          -> authorization server 의 client 인증도 이벤트가 발행된다!
	 *      토큰 발급 성공 : AuthenticationSuccessEvent (authentication=OAuth2AccessTokenAuthenticationToken)
	 *          -> grant 처리(토큰 발급)도 인증 이벤트로 잡힌다.. 별도 장치 없이 토큰 발급 감사가 가능하다는 뜻.
	 *      client 인증 실패(틀린 secret) : AuthenticationFailureProviderNotFoundEvent
	 *          -> BadCredentials 계열이 아니라 "No AuthenticationProvider found.." 이벤트로 수신된다.
	 *          todo.. 틀린 secret 인데 왜 ProviderNotFound 로 매핑되는지 ProviderManager 와 DefaultAuthenticationEventPublisher 의
	 *              예외->이벤트 매핑 경로를 추적해볼 것 (OAuth2AuthenticationException 은 기본 매핑에 없음)
	 *
	 *      참고. form login 성공 시 AbstractAuthenticationProcessingFilter 가 InteractiveAuthenticationSuccessEvent 를
	 *          추가 발행할 것으로 예상했으나 수신되지 않았다. todo.. 발행 조건 확인해볼 것
	 *
	 * 실행 방법
	 *      1. 서버 기동 후 http/ 의 .http 파일로 로그인 실패/성공, token 요청(client 인증) 수행
	 *      2. 서버 콘솔의 [인증 성공]/[인증 실패] 감사 로그 관찰
	 */

	public static void main(String[] args) {
		SpringApplication.run(AuthenticationEventsApplication.class, args);
	}

}
