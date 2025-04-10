package dev.starryeye.custom_social_login_client_with_form_login.model.internal_provider;

import dev.starryeye.custom_social_login_client_with_form_login.model.ProviderUser;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.Map;

public class FormUser implements ProviderUser {

    private final String id;
    private final String username;
    private final String password;
    private final String email;
    private final String profileImageUrl;
    private final String providerId;
    private final List<? extends GrantedAuthority> authorities;
    private final Map<String, Object> attributes;

    public FormUser(String id, String username, String password, String email, String profileImageUrl, String providerId, List<? extends GrantedAuthority> authorities, Map<String, Object> attributes) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
        this.providerId = providerId;
        this.authorities = authorities;
        this.attributes = attributes;
    }

    @Override
    public String getId() {
        return this.id;
    }

    @Override
    public String getUsername() {
        return this.username;
    }

    @Override
    public String getPassword() {
        return this.password;
    }

    @Override
    public String getEmail() {
        return this.email;
    }

    @Override
    public String getProfileImageUrl() {
        return this.profileImageUrl;
    }

    @Override
    public String getProviderId() {
        return this.providerId;
    }

    @Override
    public List<? extends GrantedAuthority> getAuthorities() {
        return this.authorities;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return this.attributes;
    }
}
