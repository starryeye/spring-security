package dev.starryeye.custom_social_login_client.service.security;

import dev.starryeye.custom_social_login_client.model.external_provider.ProviderUser;
import dev.starryeye.custom_social_login_client.service.UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class CustomOidcUserService extends AbstractOAuth2UserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    // 참고. OAuth2UserService 를 구현할게 아니라 OidcUserService 를 상속받아야 할 것 같지만, 사실 OidcUserService 를 Wrapping 함.

    private final OidcUserService oidcUserService;

    public CustomOidcUserService(UserService userService) {
        super(userService);
        this.oidcUserService = new OidcUserService();
    }

    /**
     * OidcAuthorizationCodeAuthenticationProvider 에 의해 호출 되며
     *      loadUser 호출 이후 인증 처리를 완료한다. (인증 객체 생성 및 리턴)
     */
    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {

        ClientRegistration clientRegistration = userRequest.getClientRegistration();
        OidcUser oidcUser = oidcUserService.loadUser(userRequest); // id token (+ "/userinfo) 에서 사용자 정보 얻기

        // providerUser 생성
        ProviderUser providerUser = super.providerUser(clientRegistration, oidcUser);
        // 회원 가입
        super.register(clientRegistration, providerUser);

        return oidcUser;
    }
}
