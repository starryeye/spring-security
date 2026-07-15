package dev.starryeye.custom_oauth2_token_customizer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomOauth2TokenCustomizerApplication {

	/**
	 * OAuth2TokenCustomizer 로 발행되는 토큰의 claim 을 커스텀해본다.
	 *      jpa/advance/custom-jwk-source 프로젝트에서 kid 지정용으로 스치듯 사용했던 확장 지점의 본편이다.
	 *
	 * 왜 필요한가..
	 *      기본 access token 의 claim 은 iss, sub, aud, scope, exp 등 표준 값들 뿐이다.
	 *      resource server 는 기본적으로 scope claim 만 SCOPE_* 권한으로 매핑하므로..
	 *      사용자 권한(ROLE_*) 기반 인가를 하려면 access token 에 권한 claim 을 직접 넣어줘야 한다.
	 *          (resource server 쪽에서는 커스텀 JwtAuthenticationConverter 로 이 claim 을 권한으로 매핑한다..
	 *           resource-server/custom-jwt-authentication-converter 프로젝트와 짝이 되는 authorization server 쪽 작업)
	 *
	 * 동작 구조..
	 *      OAuth2TokenCustomizer<JwtEncodingContext> 빈을 등록하면..
	 *      OAuth2ConfigurerUtils::getJwtCustomizer 가 getOptionalBean 으로 가져가 JwtGenerator 에 적용한다.
	 *      JwtGenerator 가 만드는 모든 JWT (access token, id token) 발행 직전에 호출되므로..
	 *      context.getTokenType() 으로 어떤 토큰을 만드는 중인지 분기해야 한다. (AuthorizationServerConfig 참고)
	 *
	 * 참고. opaque(REFERENCE 포맷) access token 의 경우..
	 *      JWT 가 아니므로 JwtGenerator 가 아닌 OAuth2AccessTokenGenerator 가 생성하며..
	 *      OAuth2TokenCustomizer<OAuth2TokenClaimsContext> 타입의 빈을 등록하면 된다. (OAuth2ConfigurerUtils::getAccessTokenCustomizer)
	 *
	 * 확인 포인트
	 *      1. authorization code grant 의 access token 에 authorities claim(로그인 사용자의 권한)이 추가된다.
	 *      2. id token 에는 authorities 없이 nickname claim 이 추가된다. (토큰 타입 분기 확인)
	 *      3. client credentials grant 의 access token.. principal 이 사용자가 아니라 client 인증 객체라서 authorities 가 다르다. (아래 관찰 결과)
	 *
	 * 관찰 결과
	 *      authorization code grant 의 access token : "authorities": ["ROLE_USER", "ROLE_CUSTOMER"] (로그인 사용자 권한), nickname 없음
	 *      id token : "nickname": "user-nickname", authorities 없음 -> 토큰 타입 분기가 의도대로 동작
	 *      client credentials grant 의 access token : sub 가 사용자가 아닌 client_id 이고 "authorities": [] (빈 배열)
	 *          -> principal 인 OAuth2ClientAuthenticationToken 의 권한이 빈 컬렉션이기 때문..
	 *             사용자가 없는 grant 라서 사용자 권한 기반 인가가 애초에 성립하지 않는다. (server to server 는 scope 로 인가하는 것이 자연스러움)
	 *
	 * 실행 방법
	 *      1. 서버 기동 후 http/ 의 .http 파일로 grant 수행
	 *      2. "/token-claims?token=" 으로 발급된 토큰의 claim 관찰 (http/api.http)
	 */

	public static void main(String[] args) {
		SpringApplication.run(CustomOauth2TokenCustomizerApplication.class, args);
	}

}
