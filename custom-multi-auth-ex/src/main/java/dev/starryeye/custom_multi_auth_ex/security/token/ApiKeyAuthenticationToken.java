package dev.starryeye.custom_multi_auth_ex.security.token;

import org.springframework.security.authentication.AbstractAuthenticationToken;

public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final String apiKey;

    private ApiKeyAuthenticationToken(String apiKey) {
        super(null);
        this.apiKey = apiKey;
        this.setAuthenticated(false);
    }

    public static ApiKeyAuthenticationToken unauthenticated(String apiKey) {
        return new ApiKeyAuthenticationToken(apiKey);
    }

    @Override
    public Object getCredentials() {
        return apiKey;
    }

    @Override
    public Object getPrincipal() {
        return null; // 인증 후 UsernamePasswordAuthenticationToken 으로 대체
    }
}
