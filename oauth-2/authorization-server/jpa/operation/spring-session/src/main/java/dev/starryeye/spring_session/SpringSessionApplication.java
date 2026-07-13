package dev.starryeye.spring_session;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpringSessionApplication {

	/**
	 * Spring Session + Redis 로 authorization server 의 로그인 세션을 외부화해본다. (운영 시리즈 4번, 다중 인스턴스의 마지막 조각)
	 *
	 * 문제..
	 *      authorization server 의 form login 상태는 HttpSession(인스턴스 메모리)에 저장된다.
	 *          SecurityContext(인증 객체), SavedRequest(로그인 직전의 "/oauth2/authorize" 요청) 가 세션에 있다.
	 *      -> LB 뒤에 인스턴스를 2대 이상 두면.. A 에서 로그인해도 B 로 라우팅되는 순간 재로그인을 요구한다.
	 *      -> 재기동하면 사용자 전원이 로그아웃된다.
	 *      참고. consent 진행 상태(state 단계의 OAuth2Authorization)는 세션이 아니라 OAuth2AuthorizationService 에 있다.
	 *          (custom-oauth2-authorization-service 프로젝트에서 관찰)
	 *          즉, 세션 외부화 + 토큰 저장소 외부화가 되면 인가 flow 전체가 인스턴스 무관해진다.
	 *
	 * 해결.. spring-session-data-redis
	 *      의존성 + redis 접속 설정만으로 자동 구성된다. (직접 구현할 것이 없어 custom- 없이 붙여서 알아보는 프로젝트)
	 *      SessionRepositoryFilter 가 요청의 HttpSession 을 Spring Session 구현(Redis 저장)으로 바꿔치기하는 구조라..
	 *      SecurityContext 를 세션에 저장하는 spring security 의 동작은 그대로인데 저장 위치만 Redis 가 된다.
	 *      "spring:session:sessions:{session id}" 키로 저장되며 세션 타임아웃이 TTL 로 걸린다. ("/sessions/raw" 로 관찰)
	 *
	 * 확인 포인트 (쿠키는 host 기준이고 port 를 구분하지 않는 것을 이용해 두 인스턴스로 데모)
	 *      1. 두 인스턴스 세션 공유..
	 *          8091 에서 로그인 -> 같은 세션 쿠키로 8092 의 "/oauth2/authorize" 요청 -> 재로그인 없이 consent 로 진행된다.
	 *          (세션 외부화가 없다면 8092 는 세션이 없어 로그인 페이지로 보냈을 것)
	 *      2. 재기동 후 로그인 유지..
	 *          로그인 -> 재기동 -> 같은 쿠키로 요청해도 재로그인 없음. (InMemory 세션이었다면 로그아웃)
	 *      3. Redis 의 spring:session:* 키와 TTL 관찰
	 *
	 * 실행 방법
	 *      1. docker-compose/docker-compose.yml 로 Redis 기동
	 *      2. 인스턴스 2대 기동 (다중 인스턴스는 같은 issuer 를 공유한다.. LB 뒤의 모습과 동일)
	 *          ./gradlew bootJar
	 *          java -jar build/libs/spring-session-0.0.1-SNAPSHOT.jar --server.port=8091
	 *          java -jar build/libs/spring-session-0.0.1-SNAPSHOT.jar --server.port=8092
	 *      3. web browser 로 8091 로그인 후, 주소만 8092 로 바꿔 "/oauth2/authorize" 요청 (http/ 참고)
	 */

	public static void main(String[] args) {
		SpringApplication.run(SpringSessionApplication.class, args);
	}

}
