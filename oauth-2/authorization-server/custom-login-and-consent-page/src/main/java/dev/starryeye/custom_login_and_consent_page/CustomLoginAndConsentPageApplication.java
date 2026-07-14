package dev.starryeye.custom_login_and_consent_page;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomLoginAndConsentPageApplication {

	/**
	 * authorization server 의 로그인 페이지와 consent(동의) 페이지를 커스텀 화면으로 교체해본다.
	 *      기존 프로젝트들은 전부 프레임워크가 생성해주는 기본 화면을 사용했다.
	 *
	 * 기본 화면들은 어디서 만들어지나..
	 *      로그인 페이지 : DefaultLoginPageGeneratingFilter 가 생성한다.
	 *          formLogin 의 loginPage() 를 설정하면 이 필터가 등록되지 않고 설정한 URI 로 redirect 된다.
	 *      consent 페이지 : OAuth2AuthorizationEndpointFilter 가 HTML 을 직접 생성해 응답한다. (sendAuthorizationConsent)
	 *          OAuth2AuthorizationServerConfigurer 의 authorizationEndpoint().consentPage() 를 설정하면..
	 *          HTML 생성 대신 설정한 URI 로 redirect 하며, 쿼리 파라미터로 client_id, scope, state 를 넘겨준다.
	 *
	 * 커스텀 consent 페이지(컨트롤러)의 책임.. 기본 페이지가 해주던 일을 직접 재현해야 한다. (ConsentController 참고)
	 *      1. state 는 그대로 hidden 으로 유지해서 되돌려줘야 한다.
	 *          state 는 진행 중인 인가(OAuth2Authorization)를 다시 찾는 내부 조회 키이다. (jpa/custom-oauth2-authorization-service 주석 참고)
	 *      2. openid scope 는 동의 대상이 아니므로 화면에서 제외한다.
	 *      3. OAuth2AuthorizationConsentService 를 조회해서.. 기승인 scope 는 표시만 하고, 신규 scope 만 체크박스로 승인받는다.
	 *      4. 제출은 기본 페이지와 동일하게 POST "/oauth2/authorize" 로 한다. (체크된 scope 만 전달, 아무것도 없으면 거부로 처리되어 access_denied)
	 *
	 * 확인 포인트
	 *      1. "/oauth2/authorize" 요청 -> 커스텀 로그인 화면 -> 커스텀 consent 화면 -> code -> token 까지 완주된다.
	 *      2. 일부 scope 만 동의한 뒤 전체 scope 로 재요청하면.. 기승인 scope 는 "이미 동의함" 표시로, 신규 scope 만 체크박스로 나온다.
	 *      3. 기승인이 하나도 없는 첫 동의에서 아무 scope 도 체크하지 않고 제출하면 access_denied 로 redirect 된다.
	 *          단, 기승인 scope 가 있는 상태라면 빈 제출이어도 거부가 아니라 기승인 범위로 code 가 발급된다. (실행해서 확인한 동작)
	 *
	 * 실행 방법
	 *      1. 서버 기동 후 web browser 로 http/ 의 authorize.http 요청 수행
	 *      2. 커스텀 화면들을 거쳐 code 발급 -> token.http 로 교환
	 */

	public static void main(String[] args) {
		SpringApplication.run(CustomLoginAndConsentPageApplication.class, args);
	}

}
