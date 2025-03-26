package dev.starryeye.custom_social_login_client.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class OAuth2ProviderUser implements ProviderUser {

    private final OAuth2User oAuth2User;
    private final ClientRegistration clientRegistration;
    private final Map<String, Object> attributes;

    public OAuth2ProviderUser(OAuth2User oAuth2User, ClientRegistration clientRegistration, Map<String, Object> attributes) {
        this.oAuth2User = oAuth2User;
        this.clientRegistration = clientRegistration;
        this.attributes = attributes;
    }

    @Override
    public String getPassword() {
        return UUID.randomUUID().toString();
    }

    @Override
    public String getEmail() {
        return "";
    }

    @Override
    public String getProviderId() {
        return clientRegistration.getRegistrationId();
    }

    @Override
    public List<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return Map.of();
    }
}
