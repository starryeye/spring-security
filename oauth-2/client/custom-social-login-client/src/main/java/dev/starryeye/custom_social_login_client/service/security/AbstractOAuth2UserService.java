package dev.starryeye.custom_social_login_client.service.security;

import dev.starryeye.custom_social_login_client.model.external_provider.GoogleUser;
import dev.starryeye.custom_social_login_client.model.external_provider.KeycloakUser;
import dev.starryeye.custom_social_login_client.model.external_provider.NaverUser;
import dev.starryeye.custom_social_login_client.model.external_provider.ProviderUser;
import dev.starryeye.custom_social_login_client.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.user.OAuth2User;

@Slf4j
public abstract class AbstractOAuth2UserService {

    /**
     * AbstractOAuth2UserService 를 상속한 객체의 loadUser 메서드에서는 아래 두개의 메서드를 추가적으로 동작시킨다.
     * 즉, 기존의 OAuth2UserService::loadUser 동작에서 register, providerUser 를 추가적으로 작동시킴
     */

    private final UserService userService;

    public AbstractOAuth2UserService(UserService userService) {
        this.userService = userService;
    }

    protected void register(ClientRegistration clientRegistration, ProviderUser providerUser) {

        if (userService.existsBy(providerUser.getUsername())) {
//            throw new UserAlreadyExistsException(providerUser.getUsername());
            log.info("User {} already exists", providerUser.getUsername());
            return;
        }

        userService.register(clientRegistration.getRegistrationId(), providerUser);
    }

    protected ProviderUser providerUser(ClientRegistration clientRegistration, OAuth2User oAuth2User) {

        String registrationId = clientRegistration.getRegistrationId();

        return switch (registrationId) {
            case "my-google" -> new GoogleUser(oAuth2User, clientRegistration);
            case "my-naver" -> new NaverUser(oAuth2User, clientRegistration);
            case "my-keycloak" -> new KeycloakUser(oAuth2User, clientRegistration);
            default -> throw new IllegalStateException("Unexpected value: " + registrationId);
        };
    }
}
