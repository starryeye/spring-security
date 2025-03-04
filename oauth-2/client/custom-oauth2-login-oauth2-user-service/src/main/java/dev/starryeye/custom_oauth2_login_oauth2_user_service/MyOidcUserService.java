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
     *
     * OAuth2LoginAuthenticationProvider 는..
     *      code 로 access token 교환을 위해, OAuth2AuthorizationCodeAuthenticationProvider 를 이용한다.
     *      access token 으로 userinfo 요청하여 사용자 인증 처리를 위해, DefaultOAuth2UserService 를 이용한다.
     * OidcAuthorizationCodeAuthenticationProvider 는..
     *      code 로 access token 및 id token 교환을 위해, OAuth2AccessTokenResponseClient 를 이용한다.
     *      id token 을 검증을 함으로써 인증 처리를 한다. 이를 위해 OidcUserService 를 이용한다.
     *          참고.. OidcUserService 내부에서 DefaultOAuth2UserService 의존성을 가지고 있다.
     *              OidcUserService::shouldRetrieveUserInfo 결과값에 의해 userinfo 요청을 한다. (userinfo 요청을 해서 채워야할 데이터가 있는지 조건 체크인듯..)
     *
     *
     *
     */

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        return super.loadUser(userRequest);
    }
}
