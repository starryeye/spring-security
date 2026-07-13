package dev.starryeye.custom_oauth2_authorization_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomOauth2AuthorizationServiceApplication {

	/**
	 * OAuth2AuthorizationService 를 JPA(MySQL) 구현체로 교체하여 인가/토큰 상태를 영속화해본다.
	 *      OAuth2Authorization, OAuth2AuthorizationService 의 개념은 oauth2-authorization-service 프로젝트 주석 참고.
	 *
	 * 영속화 관점에서 OAuth2Authorization 이 까다로운 이유..
	 *      인가 한 건의 상태 덩어리로.. 토큰 4종(code, access token, refresh token, id token)의 값/발급/만료/metadata 에 더해
	 *      attributes 에 principal 의 Authentication 객체와 OAuth2AuthorizationRequest 가 통째로 들어있다.
	 *      -> Map 계열(attributes, 토큰별 metadata)은 JSON 문자열로 직렬화하여 저장한다.
	 *          기본 ObjectMapper 로는 불가능하고 SecurityJackson2Modules + OAuth2AuthorizationServerJackson2Module 등록이 필수이다. (JpaOAuth2AuthorizationService 참고)
	 *      -> JWT 값과 직렬화된 attributes 는 수 KB 를 넘기 쉬워 @Lob(MySQL longtext) 컬럼을 사용한다. (OAuth2AuthorizationEntity 참고)
	 *
	 * 영속화하면 무엇이 달라지나..
	 *      InMemory(기본값) 는 재기동하면 발급된 모든 code/token 상태(OAuth2Authorization)가 사라진다.
	 *          JWT 자체는 서명만 유효하면 resource server 검증을 통과하지만.. introspect, revoke, refresh 등 저장소를 참조하는 기능이 전부 끊긴다.
	 *      DB 영속화하면 재기동 후에도 refresh token grant, introspect 등이 그대로 동작한다. (확인 포인트 3)
	 *
	 * introspect("/oauth2/introspect") 가 끊긴다는 것의 의미..
	 *      introspect 는 서명 검증이 아니라 "저장소 조회" 기반 검증이다.
	 *          OAuth2TokenIntrospectionAuthenticationProvider 가 findByToken 으로 OAuth2Authorization 을 조회해서..
	 *          찾으면 active 판정(!isInvalidated() && !isExpired() && !isBeforeUse()).. 못 찾으면 "모르는 토큰" 으로 active:false 응답 (에러가 아님)
	 *          (custom-token-introspection-endpoint 프로젝트 주석 참고)
	 *      -> InMemory 재기동으로 기록이 사라지면.. 서명이 유효한 JWT 를 들고 와도 active:false 가 된다는 뜻이다.
	 *
	 * access token 포맷(JWT/opaque)과 저장의 관계..
	 *      spring authorization server 는 토큰 포맷과 무관하게 항상 OAuth2Authorization 을 저장한다. (JWT 기본값인 이 프로젝트에서도 아래 관찰 결과처럼 save 가 일어남)
	 *          revoke 는 서버가 invalidated 상태를 기록해야 가능하고(JWT 는 자체 취소 불가.. custom-token-revocation-endpoint 주석 참고),
	 *          refresh token 은 항상 opaque 라 저장소 조회가 필수이기 때문.
	 *      포맷이 가르는 것은 저장 여부가 아니라 "resource server 의 검증 경로" 이다..
	 *          JWT(self-contained, 기본값) : resource server 가 JWKS 로 자체 검증.. introspect 불필요 (대신 revoke 가 즉시 반영되지 않음)
	 *          opaque(TokenSettings.accessTokenFormat 을 REFERENCE 로 설정) : 토큰에 정보가 없어 resource server 가 매번 introspect 로 조회해야함
	 *
	 * 관찰 결과에 등장하는 state 에 대하여..
	 *      client 가 CSRF 방지용으로 보내는 표준 state 파라미터와는 이름만 같은 다른 값이다. (client 의 state 는 attributes 안 OAuth2AuthorizationRequest 에 저장됨)
	 *      consent 가 필요할 때 authorization server 가 자체 생성하는 값으로.. consent 화면에 hidden input 으로 내려갔다가 동의 제출 시 함께 돌아와서
	 *      진행 중이던 OAuth2Authorization 을 findByToken(state) 으로 다시 찾기 위한 조회 키이다.
	 *      (entity 에서 state 를 컬럼으로 분리한 것도 이 조회 때문.. Jdbc 공식 스키마와 동일)
	 *
	 * 운영 참고..
	 *      InMemory 구현체와 달리 Jdbc/JPA 저장소는 만료된 row 를 자동 삭제하지 않는다.
	 *      grant 수행마다 row 가 쌓이므로 운영에서는 만료 데이터 purge 배치가 필요하다.
	 *
	 * 확인 포인트
	 *      1. LoggingOAuth2AuthorizationService 로 grant flow 동안의 save/findByToken 호출 시퀀스를 로그로 관찰한다. (아래 관찰 결과)
	 *      2. "/oauth2-authorizations/raw" 로 직렬화된 attributes(@class 포함 JSON), code metadata 의 invalidated=true 갱신을 관찰한다.
	 *      3. 서버를 재기동해도 refresh token grant 가 성공한다. (InMemory 였다면 invalid_grant)
	 *
	 * 관찰 결과.. authorization code grant (consent 포함) 한 사이클의 호출 시퀀스
	 *      1. [save] 보유 토큰=state
	 *          "/oauth2/authorize" 처리.. consent 가 필요하면 state 만 담긴 OAuth2Authorization 을 저장
	 *      2. [findByToken] tokenType=state
	 *          consent 동의 제출 처리 시 state 로 조회
	 *      3. [save] 보유 토큰=code(invalidated=false)
	 *          동의 완료.. authorization code 를 발급해 저장 (state 는 제거되어 있음)
	 *      4. [findByToken] tokenType=code.. 연속 2회
	 *          "/oauth2/token" 처리 시 code 로 조회하는데 2회 조회된다.
	 *          CodeVerifierAuthenticator(PKCE 검증)와 OAuth2AuthorizationCodeAuthenticationProvider 가 각각 조회하기 때문 (oauth2-authorization-service 프로젝트 주석과 일치)
	 *      5. [save] 보유 토큰=code(invalidated=true), accessToken, refreshToken, idToken
	 *          토큰 3종을 발급해 저장하면서 1회용인 code 는 invalidated=true 로 갱신
	 *
	 * 관찰 결과.. refresh token grant
	 *      1. [findByToken] tokenType=refresh_token
	 *      2. [save] 새 access token, id token 으로 갱신 저장 (reuseRefreshTokens(false) 라서 refresh token 도 새 값으로 교체됨)
	 *
	 * 실행 방법
	 *      1. docker-compose/docker-compose.yml 로 MySQL 기동
	 *      2. 서버 기동 후 http/ 의 .http 파일로 authorization code grant, refresh token grant 수행
	 *      3. "/oauth2-authorization?token=", "/oauth2-authorizations/raw" 로 저장 상태 관찰 (http/api.http)
	 */

	public static void main(String[] args) {
		SpringApplication.run(CustomOauth2AuthorizationServiceApplication.class, args);
	}

}
