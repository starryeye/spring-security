package dev.starryeye.client_registration_endpoint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ClientRegistrationEndpointApplication {

	/**
	 * dynamic client registration (DCR) 에 대해 알아본다.
	 *      client 를 관리자가 미리 등록(pre-registration)하는 것이 아니라..
	 *      client 스스로 런타임에 표준 API 로 자신을 등록하는 방식이다. (RFC 7591, OIDC Dynamic Client Registration 1.0)
	 *
	 * pre-registration 과의 대비..
	 *      client 등록 화면/API 는 프레임워크가 제공하지 않아 직접 만들어야 한다는 것이 spring authorization server 의 기본 입장인데..
	 *          (jpa/custom-registered-client-repository, practice/production-ready-authorization-server 의 admin API 가 그 구현)
	 *      이 표준 엔드포인트가 프레임워크 차원에서 제공되는 유일한 등록 경로다.
	 *      아무나 등록할 수는 없고.. "등록할 자격" 을 증명하는 access token (initial access token) 이 필요하다.
	 *
	 * 설정.. (AuthorizationServerConfig 참고)
	 *      기본 비활성이라 oidc 설정에서 clientRegistrationEndpoint 를 켜야 한다. 엔드포인트는 "/connect/register".
	 *      등록 요청은 scope "client.create" 를 가진 access token 을 Bearer 로 실어야 하므로..
	 *      같은 필터 체인에 oauth2ResourceServer(jwt) 를 함께 설정해야 한다.
	 *          -> authorization server 가 자기가 발행한 토큰의 resource server 역할을 겸하는 지점이다.
	 *
	 * 내부 동작..
	 *      OidcClientRegistrationAuthenticationProvider 의 (private) registerClient 가 등록 요청의 metadata 를
	 *      RegisteredClient 로 변환해 RegisteredClientRepository::save 를 호출한다.
	 *      -> 프레임워크가 save 를 호출하는 두 경로 중 하나의 본편이다.
	 *         (다른 하나는 client secret 인코딩 upgrade.. hello-jpa-authorization-server 의 JpaRegisteredClientRepository 주석 참고)
	 *      등록 응답에는 서버가 생성한 client_id/client_secret 과 함께..
	 *          registration_access_token : 등록된 client 설정 조회 전용 토큰 (scope client.read)
	 *          registration_client_uri : 조회 주소 ("/connect/register?client_id=...")
	 *      가 담긴다.
	 *
	 * 확인 포인트
	 *      1. scope client.create 토큰(initial access token)으로 POST "/connect/register" -> client 가 생성된다.
	 *      2. scope 가 없는 토큰으로 등록을 시도하면 거부된다.
	 *      3. 등록 응답의 registration_access_token 으로 GET registration_client_uri -> 등록 정보가 조회된다. (재사용 가능한지도 관찰)
	 *      4. 동적으로 등록된 client 로 authorization code grant 가 실제로 수행된다.
	 *      5. 동적으로 등록된 client 에 적용되는 기본 설정(ClientSettings/TokenSettings)을 관찰한다. ("/registered-client" 관찰용 api)
	 *
	 * 관찰 결과
	 *      1. 등록 응답.. client_id/client_secret 은 서버가 생성해 응답에서만 raw 로 1회 노출되고 (client_secret_expires_at 0 = 만료 없음)
	 *          저장소에는 "{bcrypt}.." 로 저장된다. (관리자 등록 API 를 직접 만들 때와 같은 원칙.. practice/production-ready-authorization-server 참고)
	 *      2. 동적으로 등록된 client 의 기본 ClientSettings.. require-proof-key=true (PKCE 강제), require-authorization-consent=true
	 *          -> 신원을 덜 신뢰할 수밖에 없는 동적 client 에 보수적인 기본값이 적용된다.
	 *          실제로 code_challenge 없이 authorize 하면 invalid_request 로 redirect 되고, 동의 화면도 항상 뜬다.
	 *      3. registration_access_token.. scope client.read, 만료 5분(토큰 payload 로 확인), 만료 전에는 재사용 가능(연속 조회 200).
	 *          이 토큰으로 "등록"(POST)을 시도하면 403 이다. (client.create 가 아니므로.. scope 로 등록/조회 권한이 분리됨)
	 *      4. 등록된 client 로 PKCE + 동의를 거친 authorization code grant 가 정상 수행된다.
	 *          (동의 화면 체크박스는 openid 를 제외한 profile 만.. openid 는 동의 대상 scope 가 아니다)
	 *
	 * 실행 방법
	 *      1. 서버 기동 후 http/client_credentials_grant/token.http 로 initial access token 발급 (my-registrar-client, scope client.create)
	 *      2. http/dynamic_client_registration/register.http 로 등록/조회
	 *      3. http/authorization_code_grant/ 로 등록된 client 의 code grant 수행 (user / 1111, PKCE 필수.. 관찰 결과 2)
	 */

	public static void main(String[] args) {
		SpringApplication.run(ClientRegistrationEndpointApplication.class, args);
	}

}
