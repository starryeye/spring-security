package dev.starryeye.custom_mac_and_rsa_validation.security.filter.jwt.config;

import dev.starryeye.custom_mac_and_rsa_validation.security.filter.jwt.JwtVerifierFilter;
import dev.starryeye.custom_mac_and_rsa_validation.security.filter.jwt.provider.JwtAuthenticationProvider;
import dev.starryeye.custom_mac_and_rsa_validation.signature.JwtVerifier;
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
