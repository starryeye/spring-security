package dev.starryeye.custom_social_login_client.service.security;

import dev.starryeye.custom_social_login_client.model.external_provider.ProviderUser;
import dev.starryeye.custom_social_login_client.service.UserService;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;

@Component
public class CustomOidcUserService extends AbstractOAuth2UserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final OidcUserService oidcUserService;

    public CustomOidcUserService(UserService userService) {
        super(userService);
        this.oidcUserService = new OidcUserService();
    }

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
