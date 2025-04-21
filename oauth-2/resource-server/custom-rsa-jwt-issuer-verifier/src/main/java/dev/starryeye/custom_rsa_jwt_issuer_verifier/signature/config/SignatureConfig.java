package dev.starryeye.custom_rsa_jwt_issuer_verifier.signature.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import dev.starryeye.custom_rsa_jwt_issuer_verifier.signature.JwtGenerator;
import dev.starryeye.custom_rsa_jwt_issuer_verifier.signature.JwtVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SignatureConfig {

    @Bean
    public JWK jwk() throws JOSEException {
        return new RSAKeyGenerator(2048)
                .keyID("rsaKey")
                .algorithm(JWSAlgorithm.RS256)
                .generate();
    }

    @Bean
    public JwtGenerator tokenGenerator(JWK jwk) {
        return new JwtGenerator(jwk);
    }

    @Bean
    public JwtVerifier jwtVerifier(JWK jwk) {
        try {
            return new JwtVerifier(jwk);
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }
}
