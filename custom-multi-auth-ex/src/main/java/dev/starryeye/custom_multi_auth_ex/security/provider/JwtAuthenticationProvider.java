package dev.starryeye.custom_multi_auth_ex.security.provider;

import dev.starryeye.custom_multi_auth_ex.security.service.JwtService;
import dev.starryeye.custom_multi_auth_ex.security.token.JwtAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class JwtAuthenticationProvider implements AuthenticationProvider {

    private final JwtService jwtService;

    public JwtAuthenticationProvider(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        String token = (String) authentication.getCredentials();

        String username = jwtService.validateTokenAndGetUsername(token);
        if (username == null) {
            throw new BadCredentialsException("Invalid JWT");
        }

        return new UsernamePasswordAuthenticationToken(username, token, List.of(new SimpleGrantedAuthority("ROLE_DEVELOPER")));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return JwtAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
