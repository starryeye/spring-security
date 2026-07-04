package dev.starryeye.custom_registered_client_repository;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomRegisteredClientRepositoryApplication {

	/**
	 * RegisteredClient 의 전체 필드를 JPA 엔티티로 매핑하여 영속화해본다.
	 *      hello-jpa-authorization-server 에서는 최소 필드만 매핑했지만..
	 *      여기서는 JdbcRegisteredClientRepository 의 공식 스키마(oauth2-registered-client-schema.sql)와 같은 구조로 전체를 매핑한다.
	 *
	 * 매핑 포인트 3가지.
	 * 1. collection 필드 (인증 방식, 권한 부여 방식, redirect uri, scope)
	 *      comma 구분 단일 문자열 컬럼으로 저장한다. (JdbcRegisteredClientRepository 와 동일)
	 * 2. ClientSettings, TokenSettings
	 *      내부가 Map<String, Object> 구조라서 JSON 문자열 컬럼으로 직렬화하여 저장한다.
	 *      기본 ObjectMapper 로는 TokenSettings 내부의 Duration, OAuth2TokenFormat, SignatureAlgorithm 타입을 다룰 수 없다.
	 *      JdbcRegisteredClientRepository$RegisteredClientRowMapper 에서 보면..
	 *          SecurityJackson2Modules.getModules(classLoader) 와 OAuth2AuthorizationServerJackson2Module 을 ObjectMapper 에 등록해서 해결하는 것을 볼 수 있다.
	 *          -> JpaRegisteredClientRepository 에서 동일하게 등록했다.
	 *      "/registered-clients/raw" 를 호출하면 JSON 으로 직렬화되어 저장된 컬럼 원문을 관찰할 수 있다. (@class 타입 정보가 포함된 형태)
	 * 3. Instant 필드 (clientIdIssuedAt, clientSecretExpiresAt)
	 *      MySQL datetime(6) 컬럼으로 매핑된다.
	 *      주의.. 기존 프로젝트들처럼 clientSecretExpiresAt(Instant.MAX) 로 설정하면 MySQL datetime 범위(9999-12-31 까지)를 벗어나 저장할 수 없다.
	 *          만료 없음은 null 로 표현한다. (InMemory 라서 문제없던 값이 DB 영속화에서 드러나는 함정)
	 *
	 * client secret 저장에 대하여..
	 *      ClientSecretAuthenticationProvider 에서 보면..
	 *          기본값 PasswordEncoderFactories.createDelegatingPasswordEncoder() 로 요청 secret 과 저장된 secret 을 matches 하는 것을 볼 수 있다.
	 *      즉, 저장소에는 인코딩된 secret 을 저장하면 된다.
	 *      기존 프로젝트들은 "{noop}secret" 로 저장했지만.. 여기서는 등록 API 가 "{bcrypt}..." 로 인코딩하여 DB 에 저장한다.
	 *          client 는 여전히 raw secret(등록 응답으로 받은 값) 으로 인증 요청하면 된다.
	 *
	 * client 등록은 등록 API (RegisteredClientController) 로 한다.
	 *      clientType 프리셋(CONFIDENTIAL, PUBLIC, SERVICE) 으로 3가지 유형을 등록해볼 수 있다.
	 *
	 * 확인 포인트
	 *      1. authorization code grant : bcrypt 로 저장된 secret 으로 client 인증이 성공한다.
	 *      2. PKCE (PUBLIC 유형) : ClientSettings 의 requireProofKey 가 JSON 왕복 후에도 동작한다.
	 *      3. token 응답의 expires_in : 등록 시 지정한 accessTokenTimeToLiveSeconds(TokenSettings) 가 JSON 왕복 후에도 반영된다. (기본값 300초)
	 *      4. 서버 재기동 후에도 client 가 유지된다.
	 *
	 * 실행 방법
	 *      1. docker-compose/docker-compose.yml 로 MySQL 기동
	 *      2. 서버 기동 후 등록 API 로 client 등록 (http/api.http) -> 응답의 client_id, client_secret 확보
	 *      3. http/ 의 .http 파일로 grant 별 flow 수행
	 *      4. "/registered-clients", "/registered-clients/raw" 로 저장 상태 관찰 (http/api.http)
	 */

	public static void main(String[] args) {
		SpringApplication.run(CustomRegisteredClientRepositoryApplication.class, args);
	}

}
