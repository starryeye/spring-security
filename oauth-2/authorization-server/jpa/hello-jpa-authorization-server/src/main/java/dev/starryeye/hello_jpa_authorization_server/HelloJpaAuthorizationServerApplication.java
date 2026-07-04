package dev.starryeye.hello_jpa_authorization_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HelloJpaAuthorizationServerApplication {

	/**
	 * authorization server 에 JPA(MySQL) 를 연동해본다. (기본 설정법)
	 *
	 * authorization server 에서 영속화 대상이 되는 저장소 인터페이스는 3가지이다.
	 *      RegisteredClientRepository
	 *          authorization server 에 등록된 client 정보 저장소
	 *          구현체 : InMemoryRegisteredClientRepository, JdbcRegisteredClientRepository
	 *      OAuth2AuthorizationService
	 *          OAuth2Authorization (code, access token, refresh token, id token 등의 인가 상태) 저장소
	 *          구현체 : InMemoryOAuth2AuthorizationService (기본값), JdbcOAuth2AuthorizationService
	 *      OAuth2AuthorizationConsentService
	 *          OAuth2AuthorizationConsent (resource owner 의 scope 동의 기록) 저장소
	 *          구현체 : InMemoryOAuth2AuthorizationConsentService (기본값), JdbcOAuth2AuthorizationConsentService
	 *
	 * 세 인터페이스 모두 개발자가 직접 빈으로 등록하면 등록한 빈이 사용된다. (이게 영속화의 교체 지점이다.)
	 *      OAuth2AuthorizationServerConfigurer 의 설정 과정에서 OAuth2ConfigurerUtils 를 통해 빈을 가져가는데..
	 *          OAuth2ConfigurerUtils::getRegisteredClientRepository 에서 보면..
	 *              getBean 으로 가져간다. -> RegisteredClientRepository 는 빈 등록이 없으면 기동 자체가 실패한다.
	 *          OAuth2ConfigurerUtils::getAuthorizationService, getAuthorizationConsentService 에서 보면..
	 *              getOptionalBean 으로 가져가고.. 빈이 없으면 InMemory 구현체를 new 해서 기본값으로 사용한다.
	 *
	 * 이 프로젝트에서는 셋 중 가장 단순한 RegisteredClientRepository 만 최소한의 JPA 구현체로 교체하여..
	 *      "인터페이스 구현체를 빈으로 등록하기만 하면 프레임워크가 그대로 사용한다" 를 확인한다.
	 *      엔티티 매핑 디테일과 나머지 두 저장소는 아래 후속 프로젝트에서 다룬다.
	 *          custom-registered-client-repository
	 *          custom-oauth2-authorization-service
	 *          custom-oauth2-authorization-consent-service
	 *
	 * 확인 포인트
	 *      1. 서버를 재기동해도 client 등록 정보가 DB(MySQL) 에 유지된다. (InMemory 는 휘발)
	 *      2. authorization code grant 가 진행되는 동안 show-sql 로 select 쿼리가 찍히는 시점을 관찰할 수 있다.
	 *          client 인증, authorize 요청 검증 등에서 findByClientId 로 조회한다. (registered-client-repository 프로젝트 주석 참고)
	 *      3. "{noop}" 으로 저장된 secret 이 첫 client 인증 후 "{bcrypt}.." 로 upgrade 되어있다. (RegisteredClientController 주석 참고)
	 *
	 * 실행 방법
	 *      1. docker-compose/docker-compose.yml 로 MySQL 기동
	 *      2. 서버 기동 후 등록 API 로 client 등록 (http/api.http) -> 응답의 client_id, client_secret 확보
	 *      3. http/ 의 .http 파일로 authorization code grant 수행
	 *      4. "/registered-client/{clientId}" 로 DB 에 저장된 client 조회 (http/api.http)
	 */

	public static void main(String[] args) {
		SpringApplication.run(HelloJpaAuthorizationServerApplication.class, args);
	}

}
