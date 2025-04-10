package dev.starryeye.custom_social_login_client_with_form_login.service.security;

import dev.starryeye.custom_social_login_client_with_form_login.service.security.creator.CreateProviderUserRequest;
import dev.starryeye.custom_social_login_client_with_form_login.service.security.creator.ProviderUserCreator;
import dev.starryeye.custom_social_login_client_with_form_login.model.ProviderUser;
import dev.starryeye.custom_social_login_client_with_form_login.service.UserService;
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

    public CustomOidcUserService(UserService userService, ProviderUserCreator<CreateProviderUserRequest, ProviderUser> providerUserCreator) {
        super(userService, providerUserCreator);
        this.oidcUserService = new OidcUserService();
    }
    
    /**
     * OidcAuthorizationCodeAuthenticationProvider 에 의해 호출 되며
     *      oidcUserService.loadUser 호출 이후..
     *      리턴 받은 OidcUser (사용자 정보 객체) 를 ProviderUser 로 가공 후 저장(회원가입)한다.
     *
     * 참고.
     * OAuth2LoginAuthenticationFilter 에서 사용자 정보 객체를 가지고 최종적으로 인증객체를 생성 및 저장함.
     */
    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {

        ClientRegistration clientRegistration = userRequest.getClientRegistration();
        OidcUser oidcUser = oidcUserService.loadUser(userRequest); // id token (+ "/userinfo) 에서 사용자 정보 얻기 (google 의 경우 조건이 안맞아 "/userinfo" 요청 하지 않음)

        // providerUser 생성
        CreateProviderUserRequest createProviderUserRequest = new CreateProviderUserRequest(clientRegistration, oidcUser);
        ProviderUser providerUser = super.createProviderUser(createProviderUserRequest);
        // 회원 가입
        super.register(clientRegistration, providerUser);

        return CustomPrincipal.of(providerUser);
    }
}
