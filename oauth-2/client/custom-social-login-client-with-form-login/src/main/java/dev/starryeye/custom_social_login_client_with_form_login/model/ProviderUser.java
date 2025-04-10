package dev.starryeye.custom_social_login_client_with_form_login.model;

import org.springframework.security.core.GrantedAuthority;

import java.util.List;
import java.util.Map;

public interface ProviderUser {

    String getId(); // 식별자
    String getUsername(); // 사용자 Id
    String getPassword();
    String getEmail();
    String getProfileImageUrl();

    String getProviderId();

    List<? extends GrantedAuthority> getAuthorities();

    Map<String, Object> getAttributes();

    boolean isOidcUser();
}
