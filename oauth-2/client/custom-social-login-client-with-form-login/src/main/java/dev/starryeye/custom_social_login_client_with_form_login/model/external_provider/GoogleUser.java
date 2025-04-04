package dev.starryeye.custom_social_login_client_with_form_login.model.external_provider;

import dev.starryeye.custom_social_login_client_with_form_login.model.OAuth2UserAttributes;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class GoogleUser extends OAuth2ProviderUser {

    public GoogleUser(OAuth2User oAuth2User, ClientRegistration clientRegistration) {
        super(oAuth2User, clientRegistration, OAuth2UserAttributes.ofMain(oAuth2User));
    }

    @Override
    public String getId() {
        return (String) getLayeredAttributes().getMainAttributes().get("sub");
    }

    @Override
    public String getUsername() {
        return (String) getLayeredAttributes().getMainAttributes().get("sub");
    }

    @Override
    public String getProfileImageUrl() {
        return (String) getLayeredAttributes().getMainAttributes().get("picture");
    }
}
