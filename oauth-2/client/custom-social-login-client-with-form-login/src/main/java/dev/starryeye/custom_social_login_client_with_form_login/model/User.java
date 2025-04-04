package dev.starryeye.custom_social_login_client_with_form_login.model;

import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

@Getter
public class User {

    private final String registrationId;
    private final String id;
    private final String username;
    private final String password;
    private final String email;
    private final String profileImageUrl;
    private final String providerId;
    private final List<? extends GrantedAuthority> authorities;

    @Builder
    private User(String registrationId, String id, String username, String password, String email, String profileImageUrl, String providerId, List<? extends GrantedAuthority> authorities) {
        this.registrationId = registrationId;
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
        this.profileImageUrl = profileImageUrl;
        this.providerId = providerId;
        this.authorities = authorities;
    }

    public static User createUser(String registrationId, String id, String username, String password, String email, String profileImageUrl, String providerId, List<? extends GrantedAuthority> authorities) {

        return User.builder()
                .registrationId(registrationId)
                .id(id)
                .username(username)
                .password(password)
                .email(email)
                .profileImageUrl(profileImageUrl)
                .providerId(providerId)
                .authorities(authorities)
                .build();
    }
}
