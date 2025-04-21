package dev.starryeye.custom_rsa_jwt_issuer_verifier.security.rsa_jwt.provider;

import dev.starryeye.custom_rsa_jwt_issuer_verifier.security.rsa_jwt.authentication.JwtAuthentication;
import dev.starryeye.custom_rsa_jwt_issuer_verifier.signature.JwtVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;

@RequiredArgsConstructor
public class JwtAuthenticationProvider implements AuthenticationProvider {

    private final JwtVerifier jwtVerifier;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        String token = (String) authentication.getCredentials();

        UserDetails user = jwtVerifier.verify(token);

        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return JwtAuthentication.class.isAssignableFrom(authentication);
    }
}
