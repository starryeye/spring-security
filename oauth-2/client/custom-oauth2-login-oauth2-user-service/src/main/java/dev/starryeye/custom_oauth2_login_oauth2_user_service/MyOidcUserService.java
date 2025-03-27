package dev.starryeye.custom_oauth2_login_oauth2_user_service;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class MyOidcUserService extends OidcUserService {

    /**
     * OidcUserService 타입의 빈을 직접 등록하면..
     *      OidcAuthorizationCodeAuthenticationProvider 는 개발자가 직접 등록한 MyOidcUserService 를 사용한다.
     *          참고, OAuth2ClientConfig.java 에 보면.. oauth2Login() 의 userInfoEndpoint 설정을 이용하여 명시적으로 등록하는 방식도 존재한다.
     *
     * OAuth 2.0 표준 기술을 사용할 때, client 는 resource owner 의 인증을 위해..
     *      1. authorization server 에 access token 을 얻고, access token 으로 userinfo 를 얻어서 인증 처리를 하는 방식
     *      2. OpenID Connect 1.0 프로토콜을 사용하여..
     *          authorization server 에 access token 을 얻을 때, scope 에 openid 를 사용하여 id token 을 얻어서 인증 처리를 하는 방식
     *      위 두가지가 존재한다.
     *
     * OAuth2LoginAuthenticationFilter 는..
     *      사용자의 인증을 위해.. authorization code 로 access token 을 얻고 사용자 인증처리를 해주는 필터이다.
     *      Filter 가 ProviderManager 로 AuthenticationProvider 에게 토큰 교환 및 인증 처리를 위임하는데.. 두가지로 나뉜다.
     *          1. OAuth 2.0 -> OAuth2LoginAuthenticationProvider 로 위임
     *          2. OpenID Connect 1.0 -> OidcAuthorizationCodeAuthenticationProvider 로 위임
     *      AuthenticationProvider 의 리턴 값인 OAuth2LoginAuthenticationToken 를 OAuth2AuthenticationToken(인증 객체, 사용자 정보 및 권한) 로 변환
     *          해당 인증 객체는 OAuth2LoginAuthenticationFilter 의 상위 클래스인 AbstractAuthenticationProcessingFilter::successfulAuthentication 에서 SecurityContextRepository 에 저장한다.
     *      OAuth2AuthorizedClient (access token, refresh token 등이 존재) 를 생성
     *          해당 객체는 OAuth2AuthorizedClientRepository 에 저장한다.
     *
     *
     * OAuth2LoginAuthenticationProvider 는..
     *      code 로 access token 교환을 위해, OAuth2AuthorizationCodeAuthenticationProvider 를 이용한다.
     *      access token 으로 userinfo 요청하여 사용자 인증 처리를 위해, DefaultOAuth2UserService 를 이용한다.
     *      인증 처리 이후 OAuth2LoginAuthenticationToken(인증 객체, 사용자 정보 및 권한) 를 리턴한다.
     * OidcAuthorizationCodeAuthenticationProvider 는..
     *      code 로 access token 및 id token 교환을 위해, OAuth2AccessTokenResponseClient 를 이용한다.
     *      id token 을 검증을 함으로써 인증 처리를 한다. 이를 위해 OidcUserService 를 이용한다. (실제 인증은 OidcAuthorizationCodeAuthenticationProvider::createOidcToken(), JwtDecoder 에 의해 이루어지며, JWT 를 검증함으로써 인증 처리된다.)
     *          참고.. OidcUserService 내부에서 DefaultOAuth2UserService 의존성을 가지고 있다.
     *              OidcUserService::shouldRetrieveUserInfo 결과값에 의해 userinfo 요청을 한다. (scope 에 openid 이외에 profile, email, address, phone 4가지 중 하나라도 존재하는지 체크)
     *      인증 처리 이후 OAuth2LoginAuthenticationToken(인증 객체, 사용자 정보 및 권한) 를 리턴한다.
     *
     * 참고
     * 인가 서버마다 사용자를 식별하는 username 에 해당하는 필드 이름이 다르다. (표준은 sub 이며, google 이 사용한다. keycloak 은 preferred_username 이다.)
     *      id token 에 기본적으로 사용자 정보들이 존재하는데.. 여기에는 sub 이 기본적으로 존재한다.
     *          하지만, keycloak 의 preferred_username 은 존재하지 않는다. 그래서 keycloak 은 scope 에 profile 을 추가해서..
     *          "/userinfo" 요청을 추가로 수행하여, scope.. profile 을 추가했을때 받을 수 있는 preferred_username 이 사용자 정보에 포함되도록 해야한다.
     *
     */

    /**
     * OAuth2UserService 인터페이스
     *      access token 을 사용하여 userinfo 요청으로 사용자의 정보를 얻는다.
     *      OAuth2User 타입의 객체를 리턴한다.
     *      구현체
     *          DefaultOAuth2UserService
     *              표준 OAuth 2.0 Provider 인 OAuth2LoginAuthenticationProvider 에 의해 사용된다.
     *          OidcUserService
     *              OpenID Connect 1.0 Provider 인 OidcAuthorizationCodeAuthenticationProvider 에 의해 사용된다.
     *
     * OAuth2User 인터페이스
     *      표준 OAuth 2.0 Provider 에 연결된 사용자 정보이다.
     *      인증 처리 이후 Authentication (인증 객체) 의 principal 속성에 저장된다.
     *      구현체
     *          DefaultOAuth2User
     *              DefaultOAuth2UserService 가 리턴하는 사용자 정보 객체
     *              사용자 인증에 대한 정보는 attributes 속성이다.
     *      상속
     *          OidcUser 인터페이스
     *              OpenID Connect 1.0 Provider 에 연결된 사용자 정보이다.
     *              구현체
     *                  DefaultOidcUser (OidcUser 인터페이스를 구현함과 동시에 DefaultOAuth2User 를 상속했다.)
     *                      OidcUserService 가 리턴하는 사용자 정보 객체
     *                      사용자 인증에 대한 정보는 getClaims() 로 얻을 수 있다. (attributes 속성 리턴)
     *
     * todo, id token 을 public key 로 검증하는지.. 확인해볼것..
     */

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        return super.loadUser(userRequest);
    }
}
