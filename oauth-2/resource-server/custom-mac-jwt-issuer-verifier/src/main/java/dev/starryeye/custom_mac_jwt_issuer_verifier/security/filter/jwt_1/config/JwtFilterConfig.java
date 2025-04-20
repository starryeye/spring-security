package dev.starryeye.custom_mac_jwt_issuer_verifier.security.filter.jwt_1.config;

import dev.starryeye.custom_mac_jwt_issuer_verifier.security.filter.jwt_1.JwtVerifierFilter;
import dev.starryeye.custom_mac_jwt_issuer_verifier.security.filter.jwt_1.provider.JwtAuthenticationProvider;
import dev.starryeye.custom_mac_jwt_issuer_verifier.signature.JwtVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;

import java.util.List;

@Configuration
public class JwtFilterConfig {

    @Bean
    public JwtVerifierFilter jwtVerifierFilter(AuthenticationManager jwtAuthenticationManager) {
        return new JwtVerifierFilter(jwtAuthenticationManager);
    }

    @Bean
    public AuthenticationManager jwtAuthenticationManager(JwtVerifier jwtVerifier) {

        JwtAuthenticationProvider jwtAuthenticationProvider = new JwtAuthenticationProvider(jwtVerifier);

        return new ProviderManager(
                List.of(jwtAuthenticationProvider),
                null
        );
    }
}
