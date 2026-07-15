package dev.starryeye.oauth2_authorization_purge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling // @Scheduled 배치(OAuth2AuthorizationPurgeScheduler) 활성화
@SpringBootApplication
public class Oauth2AuthorizationPurgeApplication {

	/**
	 * JPA 로 영속화된 OAuth2Authorization 의 만료 row 를 정리하는 배치를 만들어본다. (운영 필수 2순위)
	 *
	 * 문제..
	 *      custom-oauth2-authorization-service 프로젝트 주석에 적었듯..
	 *      InMemory 와 달리 Jdbc/JPA 저장소는 만료된 row 를 자동 삭제하지 않는다.
	 *      grant 한 번마다 oauth2_authorization row 가 하나씩 영구히 쌓이므로..
	 *      운영에서는 테이블 비대 -> 조회 성능 저하 -> 디스크 문제로 이어진다. (기능 장애는 아니지만 언젠가 반드시 터지는 유형)
	 *
	 * 해결.. @Scheduled 배치로 "보유한 모든 토큰이 만료된 row" 를 주기 삭제한다. (OAuth2AuthorizationPurgeScheduler 참고)
	 *      state 만 있는 진행 중 인가 row 는 제외 (삭제 기준 설계는 스케줄러 주석 참고)
	 *
	 * 관찰을 위한 설정..
	 *      TokenSettings 로 토큰 수명을 짧게 설정했다. (code 60초, access 30초, refresh 60초)
	 *      배치 주기 20초.. grant 수행 후 1~2분 안에 row 가 삭제되는 것을 로그와 "/oauth2-authorizations/raw" 로 관찰할 수 있다.
	 *
	 * 확인 포인트
	 *      1. grant 수행 -> row 생성 -> 모든 토큰 만료 후 첫 배치에서 삭제 ([purge] 로그)
	 *      2. client_credentials row (access token 만 보유) 는 30초 뒤, openid 없는 code grant row 는 refresh 만료(60초) 뒤 삭제된다.
	 *      3. consent 화면까지만 진행하고 이탈한 row (state 만 보유) 는 삭제되지 않고 남는다. (의도된 제외.. 스케줄러 주석 참고)
	 *
	 * 관찰 결과..
	 *      client_credentials row : access 만료(30초) 후 첫 배치에서 삭제됨
	 *      openid 포함 code grant row : refresh(60초)가 만료되어도 삭제되지 않는다..
	 *          id token 의 만료가 30분이기 때문 (TokenSettings 로 조정 불가, JwtGenerator 에 30분 고정.. 스케줄러 주석 참고)
	 *          -> openid scope 사용 시 row 의 실질 수명 하한은 30분이다.
	 *      openid 없는 code grant row (scope=profile 등) : refresh 만료(60초) 후 첫 배치에서 삭제됨
	 *      state-only row : 계속 남는다 (의도된 제외)
	 *
	 * 실행 방법
	 *      1. docker-compose/docker-compose.yml 로 MySQL 기동
	 *      2. 서버 기동 후 http/ 의 .http 파일로 grant 수행
	 *      3. [purge] 로그와 "/oauth2-authorizations/raw" 로 row 증감 관찰
	 */

	public static void main(String[] args) {
		SpringApplication.run(Oauth2AuthorizationPurgeApplication.class, args);
	}

}
