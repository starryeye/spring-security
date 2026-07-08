package dev.starryeye.custom_oauth2_authorization_consent_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomOauth2AuthorizationConsentServiceApplication {

	/**
	 * OAuth2AuthorizationConsentService 를 JPA(MySQL) 구현체로 교체하여 동의 기록을 영속화해본다.
	 *      영속화 대상 3개 인터페이스 중 마지막 조각이다. (hello-jpa-authorization-server 주석 참고)
	 *
	 * OAuth2AuthorizationConsent..
	 *      "어떤 사용자(principalName)가 어떤 client(registeredClientId) 에 어떤 scope 를 동의했는가" 를 담는 객체이다.
	 *      동의한 scope 는 "SCOPE_{scope}" 형태의 GrantedAuthority 로 저장된다.
	 *      requireAuthorizationConsent(true) client 에서만 의미가 있다.
	 *
	 * 동작 원리.. (custom-authorization-endpoint 프로젝트의 flow 주석과 대응)
	 *      "/oauth2/authorize" 처리 시 OAuth2AuthorizationCodeRequestAuthenticationProvider 가 findById 로 기승인 여부를 조회하여..
	 *          요청 scope 가 전부 기승인이면 consent 화면 없이 바로 code 를 발급하고..
	 *          아니면 consent 화면을 응답한다.
	 *      사용자가 동의를 제출하면 OAuth2AuthorizationConsentAuthenticationProvider 가 save 로 동의 기록을 저장한다.
	 *
	 * 영속화하면 무엇이 달라지나..
	 *      기본값 InMemoryOAuth2AuthorizationConsentService 는 재기동하면 동의 기록이 사라져서 사용자에게 매번 다시 동의를 받는다.
	 *      DB 영속화하면 재기동 후에도 기승인 scope 에 대해 consent 화면이 생략된다. (확인 포인트 2)
	 *
	 * 확인 포인트
	 *      1. 같은 scope 로 재인가 요청 시 consent 화면이 생략되고 바로 code 가 발급된다.
	 *      2. 서버를 재기동해도 1번이 유지된다. (InMemory 였다면 다시 동의 화면)
	 *      3. 기승인 scope 에 새 scope 를 추가해 요청하면.. consent 화면에 새 scope 만 승인 대상으로 나온다.
	 *      4. LoggingOAuth2AuthorizationConsentService 로 findById/save 호출 시퀀스를 로그로 관찰한다.
	 *
	 * 관찰 결과.. 호출 시퀀스
	 *      1. 첫 인가 요청 : [findById] 조회결과 null(동의 기록 없음) -> consent 화면 응답
	 *      2. 동의 제출 : [findById] -> [save]
	 *          consent 화면에는 profile 만 보였지만 저장은 SCOPE_openid 까지 포함됨.. openid 는 동의 대상 scope 가 아니라 자동 포함된다.
	 *      3. 같은 scope 재인가 : [findById] 기승인 확인 -> consent 화면 없이 바로 code 발급 (save 없음)
	 *      4. 재기동 + 새 세션 재로그인 후에도 3번 그대로 유지 -> 영속화 효과
	 *      5. 기승인 + 새 scope(custom-scope) 요청 : consent 화면에 custom-scope 만 승인 대상으로 표시되고..
	 *          동의 제출 시 기존 기록과 병합되어 save 된다. (SCOPE_openid,SCOPE_profile,SCOPE_custom-scope)
	 *
	 * 실행 방법
	 *      1. docker-compose/docker-compose.yml 로 MySQL 기동
	 *      2. 서버 기동 후 http/ 의 .http 파일로 authorization code grant 수행 (첫 요청은 동의 필요)
	 *      3. 같은 요청 반복 -> consent 생략 확인, 재기동 후에도 반복 -> 유지 확인
	 *      4. "/oauth2-authorization-consents", "/oauth2-authorization-consents/raw" 로 저장 상태 관찰 (http/api.http)
	 */

	public static void main(String[] args) {
		SpringApplication.run(CustomOauth2AuthorizationConsentServiceApplication.class, args);
	}

}
