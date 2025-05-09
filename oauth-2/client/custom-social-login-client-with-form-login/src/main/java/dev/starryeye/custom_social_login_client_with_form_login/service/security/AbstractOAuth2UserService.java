package dev.starryeye.custom_social_login_client_with_form_login.service.security;

import dev.starryeye.custom_social_login_client_with_form_login.service.security.creator.ProviderUserCreator;
import dev.starryeye.custom_social_login_client_with_form_login.service.security.creator.CreateProviderUserRequest;
import dev.starryeye.custom_social_login_client_with_form_login.model.ProviderUser;
import dev.starryeye.custom_social_login_client_with_form_login.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.registration.ClientRegistration;

@Slf4j
public abstract class AbstractOAuth2UserService {

    /**
     * AbstractOAuth2UserService 를 상속한 객체의 loadUser 메서드에서는 아래 두개의 메서드를 추가적으로 동작시킨다.
     * 즉, 기존의 OAuth2UserService::loadUser 동작에서 register, providerUser 를 추가적으로 작동시킴
     */

    private final UserService userService;
    private final ProviderUserCreator<CreateProviderUserRequest, ProviderUser> providerUserCreator;

    public AbstractOAuth2UserService(UserService userService, ProviderUserCreator<CreateProviderUserRequest, ProviderUser> providerUserCreator) {
        this.userService = userService;
        this.providerUserCreator = providerUserCreator;
    }

    protected void register(ClientRegistration clientRegistration, ProviderUser providerUser) {

        if (userService.existsBy(providerUser.getUsername())) {
//            throw new UserAlreadyExistsException(providerUser.getUsername());
            log.info("User {} already exists", providerUser.getUsername());
            return;
        }

        userService.register(clientRegistration.getRegistrationId(), providerUser);
    }

    protected ProviderUser createProviderUser(CreateProviderUserRequest createProviderUserRequest) {

        return providerUserCreator.create(createProviderUserRequest);
    }
}
