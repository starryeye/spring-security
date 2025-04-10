package dev.starryeye.custom_social_login_client_with_form_login.service.security;

import dev.starryeye.custom_social_login_client_with_form_login.model.User;
import dev.starryeye.custom_social_login_client_with_form_login.model.ProviderUser;
import dev.starryeye.custom_social_login_client_with_form_login.model.external_provider.ProviderOidcUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Map;

@Getter
public class CustomPrincipal implements UserDetails, OAuth2User, OidcUser {

    /**
     * AuthenticationProvider 는 AuthenticationManager 로 부터 인증 처리를 위임 받는다.
     * AuthenticationProvider 는 인증 처리 이후 AuthenticationManager 에게 Authentication 인증 객체를 리턴한다.
     * -> 인증 처리 방법에 따라 AuthenticationProvider, AuthenticationManager, Authentication 의 구현체는 모두 다르다.
     *
     * Authentication 는 principal, credentials, authorities 를 참조할 수 있다.
     * principal 은 인증 방법에 따라 UserDetails, OAuth2User, OidcUser 가 될 수 있다.
     * 이 프로젝트에서는 form 인증, OAuth 2.0 인증 을 함께 사용하기 때문에 위 3가지 타입을 하나의 객체로 묶는 CustomPrincipal 을 개발함.
     * -> @AuthenticationPrincipal 로 CustomPrincipal 을 참조할 수 있게 됨. 모든 인증객체에서 CustomPrincipal 를 참조함
     */

    private final String username;
    private final String password;
    private final Map<String, Object> attributes; // todo, Serializable 구현필요
    private final Collection<? extends GrantedAuthority> authorities;

    private final Map<String, Object> claims;
    private final OidcUserInfo oidcUserInfo;
    private final OidcIdToken idToken;

    private final String providerId;

    private CustomPrincipal(String username, String password, Map<String, Object> attributes, Collection<? extends GrantedAuthority> authorities, Map<String, Object> claims, OidcUserInfo oidcUserInfo, OidcIdToken idToken, String providerId) {
        this.username = username;
        this.password = password;
        this.attributes = attributes;
        this.authorities = authorities;
        this.claims = claims;
        this.oidcUserInfo = oidcUserInfo;
        this.idToken = idToken;
        this.providerId = providerId;
    }

    public static CustomPrincipal ofOAuth2(ProviderUser providerUser) {
        // OAuth 2.0 인증 시 OAuth2User 대신 CustomPrincipal 을 사용

        return new CustomPrincipal(
                providerUser.getUsername(),
                providerUser.getPassword(),
                providerUser.getAttributes(),
                providerUser.getAuthorities(),
                Map.of(),
                null,
                null,
                providerUser.getProviderId()
        );
    }

    public static CustomPrincipal ofOidc(ProviderUser providerUser) {
        // OIDC 인증 시 OidcUser 대신 CustomPrincipal 을 사용

        return new CustomPrincipal(
                providerUser.getUsername(),
                providerUser.getPassword(),
                providerUser.getAttributes(),
                providerUser.getAuthorities(),
                ((ProviderOidcUser)providerUser).getClaims(), // todo, 좀더 좋은 방법 없나..
                ((ProviderOidcUser)providerUser).getUserInfo(),
                ((ProviderOidcUser)providerUser).getIdToken(),
                providerUser.getProviderId()
        );
    }

    public static CustomPrincipal of(User user) {
        // form 인증 시 UserDetails 대신 CustomPrincipal 을 사용

        return new CustomPrincipal(
                user.getUsername(),
                user.getPassword(),
                Map.of(), // todo, 검토 필요
                user.getAuthorities(),
                Map.of(),
                null,
                null,
                user.getProviderId());
    }

    @Override // -- by OAuth2User --
    public String getName() {
        return this.username;
    }

    @Override // -- by OAuth2User --
    public Map<String, Object> getAttributes() {
        return this.attributes;
    }

    @Override // -- by UserDetails, OAuth2User --
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.authorities;
    }

    @Override // -- by UserDetails --
    public String getPassword() {
        return this.password;
    }

    @Override // -- by UserDetails --
    public String getUsername() {
        return this.username;
    }

    @Override // -- by OidcUser --
    public Map<String, Object> getClaims() {
        return this.claims;
    }

    @Override // -- by OidcUser --
    public OidcUserInfo getUserInfo() {
        return this.oidcUserInfo;
    }

    @Override // -- by OidcUser --
    public OidcIdToken getIdToken() {
        return this.idToken;
    }
}
