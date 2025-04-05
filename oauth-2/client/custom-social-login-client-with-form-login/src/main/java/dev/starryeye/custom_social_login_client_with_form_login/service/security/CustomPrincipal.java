package dev.starryeye.custom_social_login_client_with_form_login.service.security;

import dev.starryeye.custom_social_login_client_with_form_login.model.User;
import dev.starryeye.custom_social_login_client_with_form_login.model.external_provider.ProviderUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public class CustomPrincipal implements UserDetails, OAuth2User, OidcUser {

    /**
     * AuthenticationProvider 는 AuthenticationManager 로 부터 인증 처리를 위임 받는다.
     * AuthenticationProvider 는 인증 처리 이후 AuthenticationManager 에게 Authentication 인증 객체를 리턴한다.
     * -> 인증 처리 방법에 따라 AuthenticationProvider, AuthenticationManager, Authentication 의 구현체는 모두 다르다.
     *
     * Authentication 는 principal, credentials, authorities 를 참조할 수 있다.
     * principal 은 인증 방법에 따라 UserDetails, OAuth2User, OidcUser 가 될 수 있다.
     *      이 프로젝트에서는 form 인증, OAuth 2.0 인증 을 함께 사용하기 때문에 위 3가지 타입을 하나의 객체로 묶는 CustomPrincipal 을 개발함.
     *          -> @AuthenticationPrincipal 로 CustomPrincipal 을 참조할 수 있게 됨. 모든 인증객체에서 CustomPrincipal 를 참조함
     */

    public CustomPrincipal(ProviderUser providerUser) {
        // OAuth 2.0 인증 시 OAuth2User, OidcUser 대신 CustomPrincipal 을 사용

        // todo
    }

    public CustomPrincipal(User user) {
        // form 인증 시 UserDetails 대신 CustomPrincipal 을 사용

        // todo
    }


    @Override // -- by OidcUser --
    public Map<String, Object> getClaims() {
        return Map.of();
    }

    @Override // -- by OidcUser --
    public OidcUserInfo getUserInfo() {
        return null;
    }

    @Override // -- by OidcUser --
    public OidcIdToken getIdToken() {
        return null;
    }

    @Override // -- by OAuth2User --
    public String getName() {
        return "";
    }

    @Override // -- by OAuth2User --
    public Map<String, Object> getAttributes() {
        return Map.of();
    }

    @Override // -- by UserDetails, OAuth2User --
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override // -- by UserDetails --
    public String getPassword() {
        return "";
    }

    @Override // -- by UserDetails --
    public String getUsername() {
        return "";
    }
}
