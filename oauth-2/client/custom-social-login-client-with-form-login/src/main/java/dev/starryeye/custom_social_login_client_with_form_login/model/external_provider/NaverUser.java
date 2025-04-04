package dev.starryeye.custom_social_login_client_with_form_login.model.external_provider;

import dev.starryeye.custom_social_login_client_with_form_login.model.OAuth2UserAttributes;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

public class NaverUser extends OAuth2ProviderUser {

    public NaverUser(OAuth2User oAuth2User, ClientRegistration clientRegistration, OAuth2UserAttributes attributes) {
        super(oAuth2User, clientRegistration, attributes);
    }

    @Override
    public String getId() {
        return (String) getAttributes().getSubAttributes().get("id");
    }

    @Override
    public String getUsername() {
        return (String) getAttributes().getSubAttributes().get("email");
    }

    @Override
    public String getProfileImageUrl() {
        return (String) getAttributes().getSubAttributes().get("profile_image");
    }
}
