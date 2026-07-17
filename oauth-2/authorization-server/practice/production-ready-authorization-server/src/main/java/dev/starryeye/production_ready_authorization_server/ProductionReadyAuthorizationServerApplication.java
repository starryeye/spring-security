package dev.starryeye.production_ready_authorization_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ProductionReadyAuthorizationServerApplication {

	/**
	 * authorization-server 폴더에서 학습한 조각들을 전부 조합하여 운영급 authorization server 를 만들어본다.
	 *      개별 주제의 상세 설명은 각 출처 프로젝트에 있으므로.. 이 프로젝트의 주석은 조합 관점만 다룬다.
	 *
	 * 구도.. (docker-compose 참고)
	 *      client -> nginx 로드밸런서(9000, round robin) -> 인스턴스 2개(8091, 8092) -> mysql / redis
	 *      요청마다 다른 인스턴스가 처리하므로.. 어떤 인스턴스에 붙어도 같은 상태를 보도록 모든 상태를 외부 저장소에 두어야 한다.
	 *      이것이 authorization server 를 수평 확장(scale-out)하기 위한 조건의 전부이다.
	 *
	 * 상태별 저장소 선택.. (성격이 다른 상태를 하나의 저장소에 몰지 않는다)
	 *      RegisteredClientRepository -> JPA/MySQL.. 관리자가 등록하는 내구성 데이터, 변경이 드물다 (jpa/custom-registered-client-repository 이식)
	 *      OAuth2AuthorizationConsentService -> JPA/MySQL.. 사용자의 동의 기록, 유지되어야 재동의를 안 물어본다 (jpa/custom-oauth2-authorization-consent-service 이식)
	 *      OAuth2AuthorizationService -> Redis.. 토큰 수명만큼만 살면 되는 회전 데이터, TTL 자동 만료로 purge 배치가 불필요 (jpa/advance/custom-redis-oauth2-authorization-service 이식)
	 *      로그인 세션 -> Spring Session + Redis.. 인스턴스 간 세션 공유 (jpa/advance/spring-session 이식)
	 *      UserDetailsService -> JPA/MySQL.. 사용자도 인스턴스 간 공유되어야 하는 내구성 상태다 (이 프로젝트에서 추가.. JpaUserDetailsService)
	 *
	 * 나머지 조합 요소..
	 *      keystore 기반 JWKSource : 부팅 시 RSA 생성이면 인스턴스마다/재기동마다 키가 달라서 다중 인스턴스가 불가능하다.. 이 구도의 필수 조건 (jpa/advance/custom-jwk-source 이식)
	 *      OAuth2TokenCustomizer : 현재 키 kid 지정(키 2개라 필수) + access token 에 authorities claim (custom-oauth2-token-customizer 이식)
	 *      커스텀 로그인/동의 페이지 (custom-login-and-consent-page 이식)
	 *      client/사용자 등록 admin API : seed 없이 API 로 사전 등록(pre-registration), secret/password 는 처음부터 bcrypt (jpa/custom-registered-client-repository 이식 + 사용자 등록 추가)
	 *      admin API 보호 : ROLE_ADMIN + http basic.. 최초 관리자만 부팅 시 부트스트랩 (이 프로젝트에서 추가.. AdminAccountInitializer)
	 *      ForwardedHeaderFilter : 프록시(nginx) 뒤에서 redirect 등 요청 기반 URL 이 내부 주소로 노출되지 않도록 한다 (etc/forwarded-header-filter 이식)
	 *      인증 이벤트 감사 로그 : 로그인/client 인증/토큰 발급 성공·실패 감사 (etc/authentication-events 이식)
	 *      공유 SessionRegistry : id token 의 sid/auth_time 이 인스턴스와 무관하게 일관되도록 spring session(redis) 기반으로 구현
	 *          (기본 InMemory registry 가 다중 인스턴스에서 왜 문제인지는 session/SpringSessionSessionRegistry 참고)
	 *
	 * 확인 포인트
	 *      1. LB(9000) 로 연속 요청하면 "/whoami" 의 instancePort 가 8091/8092 로 번갈아 나온다. (round robin)
	 *      2. authorization code grant 전체 흐름(로그인 -> 동의 -> code -> token)을 LB 로만 수행해도 끊기지 않는다.
	 *          로그인 세션(redis), 진행 중 인가 state(redis), code(redis), 동의 기록(mysql)을
	 *          매 단계 다른 인스턴스가 조회하게 되는데도 흐름이 이어진다. -> 상태 외부화의 총증명
	 *      3. 발급된 JWT 의 iss 는 LB 주소(http://localhost:9000), kid 는 keystore 의 현재 키 alias 이다.
	 *      4. 두 인스턴스를 모두 재기동해도.. 로그인 세션 유지, refresh token grant 성공, 재로그인 시 동의 생략, 기존 JWT 의 "/oauth2/jwks" 검증 유지
	 *      5. 두 인스턴스 각각의 로그에 자신이 처리한 단계의 감사 로그([인증 성공] 등)가 나뉘어 찍힌다.
	 *      6. admin API 는 관리자 basic 인증으로만 성공하고.. 미인증은 401 또는 로그인 redirect(Accept 협상, DefaultSecurityConfig 참고),
	 *          일반 사용자는 403, 틀린 관리자 비밀번호는 401 과 함께 [인증 실패] 감사 로그가 남는다.
	 *
	 * 관찰 결과 (전 과정을 LB 로만 수행한 e2e)
	 *      1. "/whoami" 연속 호출 -> instancePort 8091/8092 교대 응답 (round robin.. 기동 직후 몇 회는 한쪽으로 몰렸다가 이후 교대)
	 *      2. 한 번의 authorization code grant 의 감사 로그가 두 인스턴스에 나뉘어 찍혔다..
	 *          8091 : authorize 요청 접수, 로그인(UsernamePasswordAuthenticationToken), client 인증, 토큰 발급
	 *          8092 : 동의 제출 처리(OAuth2AuthorizationConsentAuthenticationToken), code 발급(OAuth2AuthorizationCodeRequestAuthenticationToken)
	 *          -> 매 단계 다른 인스턴스가 처리했는데도 code 와 token 이 정상 발급되었다. (상태 외부화 총증명)
	 *      3. access token : iss = "http://localhost:9000", kid = "key-2026-07-15", authorities = [ROLE_USER, ROLE_CUSTOMER]
	 *          id token : nickname 만 추가되고 authorities 없음 (토큰 타입 분기 의도대로)
	 *      4. 두 인스턴스 재기동 후.. 기존 세션 쿠키로 "/whoami" 인증 유지, refresh token grant 성공,
	 *          같은 scope 재인가는 동의 기록(MySQL)이 전부 기승인이라 consent 화면 없이 즉시 code 발급,
	 *          재기동 전 발급한 access token 이 재기동 후 JWKS 공개키로 서명 검증 통과 (키 고정 증명)
	 *      5. 미인증 redirect 의 Location 이 내부 주소가 아닌 LB 주소(http://localhost:9000/login)로 나옴 (ForwardedHeaderFilter 동작)
	 *      6. admin API 보호.. 미인증은 Accept 협상에 따라 401(curl) 또는 로그인 redirect(브라우저형 Accept),
	 *          일반 사용자(user/1111) basic 인증은 403, 틀린 관리자 비밀번호는 401 + [인증 실패] BadCredentials 감사 로그
	 *      7. 사용자 영속화.. admin API 로 등록한 사용자로 code grant 로그인 성공, access token 의 authorities = DB 저장 권한,
	 *          두 인스턴스 재기동 후에도 사용자/관리자 유지 (부트스트랩은 "이미 존재" 로 건너뜀)
	 *      8. 관리자 부트스트랩 동시 기동 경합이 실제로 발생.. 두 인스턴스가 6ms 차이로 둘 다 insert 를 시도했고
	 *          한쪽이 unique 제약 위반을 잡아 건너뛰며 정상 기동 (예외를 안 잡으면 인스턴스가 내려간다.. AdminAccountInitializer 참고)
	 *
	 *      9. OpenID conformance suite(OIDCC Basic OP 플랜, 35개 모듈) 검증 결과.. PASSED 18, WARNING 4, SKIPPED 6, 자동화 한계 5, 부적합 2
	 *          - 다중 인스턴스 결함 발견: openid token 발급 시 id token 의 auth_time 이 인스턴스별 InMemory SessionRegistry 의
	 *            세션 시각에서 나와 두 인스턴스가 서로 다른 값을 발급 (단일 인스턴스에서는 재현되지 않는 스펙 위반, 3개 모듈 실패)
	 *            -> 공유 SessionRegistry(위 조합 요소) 도입 후 3건 모두 통과
	 *          - 남은 부적합 2건은 프레임워크 영역: request object 파라미터 무시, 미인증 POST authorize 의 파라미터 유실
	 *          - 실행 방법과 전체 결과는 openid-conformance/README.md 참고
	 *
	 * 주의. nginx upstream 이름에 underscore 를 쓰면 안 된다..
	 *      proxy_pass 가 Host 헤더 기본값으로 upstream 이름을 전달하는데..
	 *      tomcat 이 underscore 포함 host 를 유효하지 않은 것으로 보고 400 을 반환한다. (hyphen 이름 사용.. nginx.conf 참고)
	 *
	 * 실행 방법
	 *      1. docker-compose/ 에서 docker-compose -p production-ready-authorization-server up -d (nginx, mysql, redis)
	 *      2. 인스턴스 2개 기동.. java -jar build/libs/*.jar 와 java -jar build/libs/*.jar --server.port=8092
	 *      3. http/api.http 로 client 등록 -> 응답의 client_id/secret 로 http/ 의 grant 수행 (전부 LB 9000 경유)
	 */

	public static void main(String[] args) {
		SpringApplication.run(ProductionReadyAuthorizationServerApplication.class, args);
	}

}
