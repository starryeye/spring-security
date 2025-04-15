package dev.starryeye.custom_multi_auth_ex.security.token;

import org.springframework.security.authentication.AbstractAuthenticationToken;

public class JwtAuthenticationToken extends AbstractAuthenticationToken {

    private final String token;

    private JwtAuthenticationToken(String token) {
        super(null);
        this.token = token;
        super.setAuthenticated(false);
    }

    public static JwtAuthenticationToken unauthenticated(String token) {
        return new JwtAuthenticationToken(token);
    }

    @Override
    public Object getCredentials() {
        return token;
    }

    @Override
    public Object getPrincipal() {
        return null; // 인증 후 UsernamePasswordAuthenticationToken 으로 대체
    }
}
