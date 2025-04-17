package dev.starryeye.custom_mac_and_rsa_validation.signature.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.gen.OctetSequenceKeyGenerator;
import dev.starryeye.custom_mac_and_rsa_validation.signature.JWTGenerator;
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
    public JWTGenerator tokenGenerator(JWK jwk) {
        return new JWTGenerator(jwk);
    }
}
