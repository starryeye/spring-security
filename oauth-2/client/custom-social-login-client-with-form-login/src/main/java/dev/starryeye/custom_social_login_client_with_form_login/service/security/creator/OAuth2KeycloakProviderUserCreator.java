package dev.starryeye.custom_social_login_client_with_form_login.service.security.creator;

import dev.starryeye.custom_social_login_client_with_form_login.model.ProviderType;
import dev.starryeye.custom_social_login_client_with_form_login.model.external_provider.KeycloakUser;
import dev.starryeye.custom_social_login_client_with_form_login.model.ProviderUser;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class OAuth2KeycloakProviderUserCreator implements ProviderUserCreator<CreateProviderUserRequest, ProviderUser> {

    @Override
    public ProviderUser create(CreateProviderUserRequest createProviderUserRequest) {

        OAuth2User oAuth2User = createProviderUserRequest.oAuth2User();
        ClientRegistration clientRegistration = createProviderUserRequest.clientRegistration();

        if (!ProviderType.KEYCLOAK.getProviderId().equals(clientRegistration.getRegistrationId())) {
            return null;
        }

        return new KeycloakUser(
                oAuth2User,
                clientRegistration
        );
    }
}
