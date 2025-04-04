package dev.starryeye.custom_social_login_client_with_form_login.model.creator;

import dev.starryeye.custom_social_login_client_with_form_login.model.OAuth2UserAttributes;
import dev.starryeye.custom_social_login_client_with_form_login.model.ProviderType;
import dev.starryeye.custom_social_login_client_with_form_login.model.external_provider.NaverUser;
import dev.starryeye.custom_social_login_client_with_form_login.model.external_provider.ProviderUser;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class OAuth2NaverProviderUserCreator implements ProviderUserCreator<CreateProviderUserRequest, ProviderUser> {

    private static final String NAVER_SUB_ATTRIBUTES_KEY = "response";

    @Override
    public ProviderUser create(CreateProviderUserRequest createProviderUserRequest) {

        OAuth2User oAuth2User = createProviderUserRequest.oAuth2User();
        ClientRegistration clientRegistration = createProviderUserRequest.clientRegistration();

        if (!ProviderType.NAVER.getProviderId().equals(clientRegistration.getRegistrationId())) {
            return null;
        }

        return new NaverUser(
                oAuth2User,
                clientRegistration,
                OAuth2UserAttributes.ofSub(oAuth2User, NAVER_SUB_ATTRIBUTES_KEY).getSubAttributes()
        );
    }
}
