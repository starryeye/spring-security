package dev.starryeye.custom_redis_oauth2_authorization_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomRedisOauth2AuthorizationServiceApplication {

	/**
	 * OAuth2AuthorizationService 를 Redis 구현체로 교체해본다. (운영 시리즈 3번)
	 *
	 * 왜 Redis 인가..
	 *      토큰 상태는 회전이 빠르고(발급/갱신/만료) 수명이 명확한 데이터라 TTL 기반 저장소와 잘 맞는다.
	 *      RDB(JPA) 저장소와의 결정적 차이는 만료 데이터 정리 방식이다..
	 *          RDB : 만료 row 가 영구히 쌓임 -> oauth2-authorization-purge 프로젝트처럼 삭제 배치가 필요
	 *          Redis : 저장할 때 TTL 을 걸어두면 스스로 사라짐 -> purge 배치 자체가 필요 없다
	 *          심지어 배치에서 제외할 수밖에 없었던 state-only(진행 중 인가) 데이터도 고정 TTL 로 정리된다.
	 *
	 * 저장 구조와 TTL 설계는 RedisOAuth2AuthorizationService 주석 참고.
	 *      JPA 프로젝트의 entity 컬럼 구성을 hash field 로 그대로 옮겼고..
	 *      attributes/metadata 의 JSON 직렬화 이슈(SecurityJackson2Modules)도 저장소만 바뀌었을 뿐 동일하게 등장한다.
	 *
	 * 확인 포인트
	 *      1. grant 수행 후 redis-cli 로 키와 TTL 을 관찰한다. (본체 hash + 토큰별 역색인 키)
	 *          client_credentials : 약 30초 TTL -> 30초 뒤 키가 스스로 사라짐 (배치 없이!)
	 *          code grant(openid 포함) : 본체 TTL 은 가장 늦게 만료되는 id token(30분 고정.. oauth2-authorization-purge 프로젝트 참고) 기준
	 *      2. 서버를 재기동해도 refresh token grant 가 성공한다. (Redis 에 상태 유지)
	 *          주의. refresh token TTL 이 60초라서 재기동 데모는 토큰 발급 후 60초 안에 수행해야 한다.
	 *              늦으면 invalid_grant 가 나오는데.. 이건 영속화 실패가 아니라 정상적인 만료 거절이다. (역색인 키도 TTL 로 이미 사라져 있음)
	 *      3. consent 화면에서 이탈한 state-only 키도 5분 TTL 로 스스로 정리된다.
	 *
	 * 실행 방법
	 *      1. docker-compose/docker-compose.yml 로 Redis 기동
	 *      2. 서버 기동 후 http/ 의 .http 파일로 grant 수행
	 *      3. "/oauth2-authorizations/raw" 또는 redis-cli(keys, ttl)로 키 상태 관찰 (http/api.http)
	 */

	public static void main(String[] args) {
		SpringApplication.run(CustomRedisOauth2AuthorizationServiceApplication.class, args);
	}

}
