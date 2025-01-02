package dev.starryeye.auth_service.security.ajax_api;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class ApiMyAuthenticationToken extends AbstractAuthenticationToken {

    /**
     * Rest 방식의 인증 객체
     */

    private final Object principal;
    private final Object credentials;

    public ApiMyAuthenticationToken(Collection<? extends GrantedAuthority> authorities, Object principal, Object credentials) {
        super(authorities);
        this.principal = principal;
        this.credentials = credentials;
        setAuthenticated(true); // 권한이 존재하므로 인증을 받은 상태이다.
    }

    public ApiMyAuthenticationToken(Object principal, Object credentials) {
        super(null);
        this.principal = principal;
        this.credentials = credentials;
        setAuthenticated(false); // 권한이 존재하지 않으므로 인증을 받기 전 상태이다.
    }

    @Override
    public Object getCredentials() {
        return this.credentials;
    }

    @Override
    public Object getPrincipal() {
        return this.principal;
    }
}
