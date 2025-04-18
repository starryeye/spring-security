package dev.starryeye.custom_mac_and_rsa_validation.security.filter.jwt.authentication;

import org.springframework.security.authentication.AbstractAuthenticationToken;

public class JwtAuthentication extends AbstractAuthenticationToken {

    private final String token;

    private JwtAuthentication(String token) {
        super(null);
        this.token = token;
        setAuthenticated(false);
    }

    public static JwtAuthentication unauthenticated(String token) {
        return new JwtAuthentication(token);
    }

    @Override
    public Object getCredentials() {
        return this.token;
    }

    @Override
    public Object getPrincipal() {
        return null; // 인증 후 UsernamePasswordAuthenticationToken 으로 대체
    }
}
