package dev.starryeye.custom_mac_and_rsa_validation.security.filter.jwt_2.config;

import com.nimbusds.jose.jwk.JWK;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(JWK jwk) {
        return NimbusJwtDecoder.withSecretKey(jwk.toOctetSequenceKey().toSecretKey())
                .macAlgorithm(MacAlgorithm.from(jwk.getAlgorithm().getName()))
                .build();
    }
}
