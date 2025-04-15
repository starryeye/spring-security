package dev.starryeye.custom_multi_auth_ex.security.provider;

import dev.starryeye.custom_multi_auth_ex.security.service.ApiKeyService;
import dev.starryeye.custom_multi_auth_ex.security.token.ApiKeyAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class ApiKeyAuthenticationProvider implements AuthenticationProvider {

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthenticationProvider(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        String apiKey = (String) authentication.getCredentials();

        String username = apiKeyService.findUsernameByApiKey(apiKey);
        if (username == null) {
            throw new BadCredentialsException("Invalid API Key");
        }

        return new UsernamePasswordAuthenticationToken(username, apiKey, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ApiKeyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
