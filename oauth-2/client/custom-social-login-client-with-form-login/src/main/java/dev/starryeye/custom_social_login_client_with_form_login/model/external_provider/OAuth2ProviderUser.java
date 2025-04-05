package dev.starryeye.custom_social_login_client_with_form_login.model.external_provider;

import dev.starryeye.custom_social_login_client_with_form_login.model.OAuth2UserAttributes;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public abstract class OAuth2ProviderUser implements ProviderUser {

    private final OAuth2User oAuth2User;
    private final ClientRegistration clientRegistration;
    private final OAuth2UserAttributes layeredAttributes;

    public OAuth2ProviderUser(OAuth2User oAuth2User, ClientRegistration clientRegistration, OAuth2UserAttributes layeredAttributes) {
        this.oAuth2User = oAuth2User;
        this.clientRegistration = clientRegistration;
        this.layeredAttributes = layeredAttributes;
    }

    @Override
    public String getPassword() {
        return UUID.randomUUID().toString();
    }

    @Override
    public String getEmail() {
        return (String) this.layeredAttributes.getMainAttributes().get("email");
    }

    @Override
    public String getProviderId() {
        return this.clientRegistration.getRegistrationId();
    }

    @Override
    public List<? extends GrantedAuthority> getAuthorities() {
        return this.oAuth2User.getAuthorities().stream().toList();
    }

    @Override
    public Map<String, Object> getAttributes() {
        return this.layeredAttributes.getMainAttributes();
    }

    protected OAuth2UserAttributes getLayeredAttributes() {
        return this.layeredAttributes;
    }
}
