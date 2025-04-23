package dev.starryeye.custom_rsa_jwt_issuer_verifier.security.rsa_jwt_2.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(JWK jwk) throws JOSEException {
        return NimbusJwtDecoder.withPublicKey(jwk.toRSAKey().toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.from(jwk.getAlgorithm().getName()))
                .build();
    }
}
