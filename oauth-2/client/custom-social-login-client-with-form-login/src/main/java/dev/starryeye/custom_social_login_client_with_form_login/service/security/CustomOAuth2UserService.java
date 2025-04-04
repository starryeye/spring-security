package dev.starryeye.custom_social_login_client_with_form_login.service.security;

import dev.starryeye.custom_social_login_client_with_form_login.model.creator.CreateProviderUserRequest;
import dev.starryeye.custom_social_login_client_with_form_login.model.external_provider.ProviderUser;
import dev.starryeye.custom_social_login_client_with_form_login.service.UserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

@Component
public class CustomOAuth2UserService extends AbstractOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService defaultOAuth2UserService;

    public CustomOAuth2UserService(UserService userService) {
        super(userService);
        this.defaultOAuth2UserService = new DefaultOAuth2UserService();
    }

    /**
     * OAuth2LoginAuthenticationProvider 에 의해 호출 되며
     *      loadUser 호출 이후 인증 처리를 완료한다. (인증 객체 생성 및 리턴)
     */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        ClientRegistration clientRegistration = userRequest.getClientRegistration();
        OAuth2User oAuth2User = defaultOAuth2UserService.loadUser(userRequest); // "/userinfo"

        CreateProviderUserRequest createProviderUserRequest = new CreateProviderUserRequest(clientRegistration, oAuth2User);

        // providerUser 생성
        ProviderUser providerUser = super.createProviderUser(createProviderUserRequest);
        // 회원 가입
        super.register(clientRegistration, providerUser);

        return oAuth2User;
    }
}
