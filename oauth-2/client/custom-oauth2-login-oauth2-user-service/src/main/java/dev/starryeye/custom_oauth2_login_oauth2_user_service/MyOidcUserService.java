package dev.starryeye.custom_oauth2_login_oauth2_user_service;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class MyOidcUserService extends OidcUserService {

    /**
     * OidcUserService 타입의 빈을 직접 등록하면, OidcAuthorizationCodeAuthenticationProvider 가 직접 등록한 MyOidcUserService 를 사용한다.
     *
     * 
     */

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        return super.loadUser(userRequest);
    }
}
