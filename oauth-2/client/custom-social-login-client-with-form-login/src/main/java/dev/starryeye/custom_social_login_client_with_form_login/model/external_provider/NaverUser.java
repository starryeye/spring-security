package dev.starryeye.custom_social_login_client_with_form_login.model.external_provider;

import dev.starryeye.custom_social_login_client_with_form_login.model.OAuth2UserAttributes;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class NaverUser extends OAuth2ProviderUser {

    private static final String NAVER_SUB_ATTRIBUTES_KEY = "response";

    public NaverUser(OAuth2User oAuth2User, ClientRegistration clientRegistration) {
        super(oAuth2User, clientRegistration, OAuth2UserAttributes.ofSub(oAuth2User, NAVER_SUB_ATTRIBUTES_KEY));
    }

    @Override
    public String getId() {
        return (String) getLayeredAttributes().getSubAttributes().get("id");
    }

    @Override
    public String getUsername() {
        return (String) getLayeredAttributes().getSubAttributes().get("email");
    }

    @Override
    public String getProfileImageUrl() {
        return (String) getLayeredAttributes().getSubAttributes().get("profile_image");
    }
}
