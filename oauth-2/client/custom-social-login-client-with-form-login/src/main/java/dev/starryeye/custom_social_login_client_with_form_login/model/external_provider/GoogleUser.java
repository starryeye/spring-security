package dev.starryeye.custom_social_login_client_with_form_login.model.external_provider;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Map;

public class GoogleUser extends OAuth2ProviderUser {

    public GoogleUser(OAuth2User oAuth2User, ClientRegistration clientRegistration, Map<String, Object> attributes) {
        super(oAuth2User, clientRegistration, attributes);
    }

    @Override
    public String getId() {
        return (String) getAttributes().get("sub");
    }

    @Override
    public String getUsername() {
        return (String) getAttributes().get("sub");
    }
}
