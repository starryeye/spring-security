package dev.starryeye.custom_mac_jwt_issuer_verifier.signature.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.gen.OctetSequenceKeyGenerator;
import dev.starryeye.custom_mac_jwt_issuer_verifier.signature.JwtGenerator;
import dev.starryeye.custom_mac_jwt_issuer_verifier.signature.JwtVerifier;
import dev.starryeye.custom_mac_jwt_issuer_verifier.signature.mac.MacJwtGenerator;
import dev.starryeye.custom_mac_jwt_issuer_verifier.signature.mac.MacJwtVerifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SignatureConfig {

    @Bean
    public JWK jwk() throws JOSEException {
        return new OctetSequenceKeyGenerator(256)
                .keyID("macKey")
                .algorithm(JWSAlgorithm.HS256)
                .generate();
    }

    @Bean
    public JwtGenerator tokenGenerator(JWK jwk) throws KeyLengthException {
        return new MacJwtGenerator(jwk);
    }

    @Bean
    public JwtVerifier jwtVerifier(JWK jwk) {
        try {
            return new MacJwtVerifier(jwk);
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }
}
