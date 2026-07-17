package dev.starryeye.token_exchange;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TokenExchangeApplication {

	/**
	 * token exchange grant (RFC 8693) 에 대해 알아본다.
	 *      가지고 있는 토큰(subject token)을 제출하고.. 대상/권한을 조정한 새 토큰을 발급받는 grant 이다.
	 *      grant_type = "urn:ietf:params:oauth:grant-type:token-exchange"
	 *
	 * 왜 필요한가.. (token relay 와의 대비)
	 *      resource server A 가 다른 resource server B 를 호출할 때..
	 *      받은 access token 을 그대로 전달(relay)하는 방식은 간단하지만 (practices/simple-integration-client-and-resource-server-and-authorization-server 의 comment 조회가 이 방식)..
	 *      B 용으로 발급된 적 없는 토큰이 B 에 도달하고, A 가 사용자 행세를 한다는 사실이 토큰에 남지 않는다.
	 *      token exchange 는 "사용자(sub)는 유지하면서 대상(aud)과 권한(scope)을 좁힌 새 토큰" 을 정식으로 발급받고..
	 *      actor token 을 함께 제출하면 "누가 대신 행동하는가" 가 act claim 으로 토큰에 남는다. (delegation)
	 *
	 * 요청 파라미터..
	 *      subject_token / subject_token_type : 교환 대상 토큰. 타입은 access_token 또는 jwt (urn:ietf:params:oauth:token-type:*)
	 *      actor_token / actor_token_type : (선택) 대리 행동의 주체 토큰.. 제출하면 delegation, 없으면 impersonation
	 *      requested_token_type, scope, audience, resource : (선택) 발급받을 토큰의 형태 조정
	 *
	 * 내부 동작..
	 *      OAuth2TokenExchangeAuthenticationConverter/Provider 가 token endpoint 에 기본 등록되어 있어서..
	 *      별도 설정 없이 client 에 grant type 만 부여하면 동작한다. (device grant 도 같은 방식으로 기본 등록되어 있다)
	 *      provider 는 subject_token(과 actor_token)을 OAuth2AuthorizationService::findByToken 으로 조회한다..
	 *      -> 이 서버가 발급하고 "저장해둔" 토큰만 교환할 수 있다. (introspect 와 같은 저장소 조회 원리.. jpa/custom-oauth2-authorization-service 참고)
	 *      subject token 에 may_act claim 이 있으면 actor 를 제한하는 검증도 수행한다. (RFC 8693 4.4)
	 *
	 * 확인 포인트
	 *      1. code grant 로 받은 사용자 access token 을 subject 로 교환 -> 새 토큰의 sub 는 여전히 사용자다. (client 가 바뀌어도 사용자 신원 유지)
	 *      2. actor_token(교환 client 자신의 client_credentials 토큰)을 함께 제출하면 act claim 이 남는다. (delegation)
	 *      3. scope 파라미터로 권한을 좁혀서 발급받을 수 있다. 반대로 subject 에 없는 scope 를 요청하면 invalid_scope.
	 *      4. exchange grant 가 없는 client 가 시도하면 unauthorized_client.
	 *      5. 이 서버가 발급하지 않은(저장소에 없는) subject_token 은 invalid_grant.
	 *
	 * 관찰 결과
	 *      subject token (code grant, my-spring-client) : sub=user, scope=[openid, profile, custom-scope]
	 *      교환(impersonation, actor 없음, scope=custom-scope 요청) : 응답 issued_token_type=access_token,
	 *          sub=user (사용자 신원 유지), aud=my-exchange-client (대상이 교환 client 로 바뀜), scope=[custom-scope] (축소), act 없음
	 *      교환(delegation, actor_token=교환 client 의 client_credentials 토큰) :
	 *          act={"iss": "http://localhost:8091", "sub": "my-exchange-client"} -> "my-exchange-client 가 user 대신 행동 중" 이 토큰에 남는다
	 *      부정 케이스..
	 *          subject 에 없는 scope(email) 요청 -> invalid_scope
	 *          exchange grant 없는 client(my-spring-client)의 교환 시도 -> unauthorized_client
	 *          이 서버가 발급하지 않은 subject_token -> invalid_grant (저장소 조회 실패.. 서명이 유효해도 저장소에 없으면 교환 불가)
	 *
	 * 실행 방법
	 *      1. http/authorization_code_grant/ 로 사용자 access token 발급 (user / 1111, my-spring-client)
	 *      2. http/token_exchange_grant/token.http 로 교환 수행 및 부정 케이스 관찰 (my-exchange-client)
	 *      3. "/token-claims?token=" 으로 발급된 토큰의 claim 관찰 (http/api.http)
	 */

	public static void main(String[] args) {
		SpringApplication.run(TokenExchangeApplication.class, args);
	}

}
