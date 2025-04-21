package dev.starryeye.custom_mac_jwt_issuer_verifier.security.filter.mac_jwt_1.config;

import dev.starryeye.custom_mac_jwt_issuer_verifier.security.filter.mac_jwt_1.provider.JwtAuthenticationProvider;
import dev.starryeye.custom_mac_jwt_issuer_verifier.signature.JwtVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;

import java.util.List;

@Configuration
public class JwtFilterConfig {

    // 주의! OncePerRequestFilter를 상속받은 filter 는 빈으로 등록하면 servlet filter 에 추가되므로 원래는 SecurityFilterChain 에서 new 해주는게 좋음..
//    @Bean
//    public JwtVerifierFilter jwtVerifierFilter(AuthenticationManager jwtAuthenticationManager) {
//        return new JwtVerifierFilter(jwtAuthenticationManager);
//    }

    @Bean
    public AuthenticationManager jwtAuthenticationManager(JwtVerifier jwtVerifier) {

        JwtAuthenticationProvider jwtAuthenticationProvider = new JwtAuthenticationProvider(jwtVerifier);

        return new ProviderManager(
                List.of(jwtAuthenticationProvider),
                null
        );
    }
}
