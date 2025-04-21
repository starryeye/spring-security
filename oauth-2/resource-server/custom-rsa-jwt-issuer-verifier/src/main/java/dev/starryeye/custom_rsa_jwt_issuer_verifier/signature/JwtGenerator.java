package dev.starryeye.custom_rsa_jwt_issuer_verifier.signature;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.List;

public class JwtGenerator {

    private static final int FIVE_MINUTE = 60 * 1000 * 5;

    private final JWK jwk;

    public JwtGenerator(JWK jwk) {
        this.jwk = jwk;
    }

    public String generateSignedToken(UserDetails userDetails) {

        try {
            // todo, refactoring
            RSAPrivateKey secretKey = jwk.toRSAKey().toRSAPrivateKey();
            JWSAlgorithm algorithm = (JWSAlgorithm) jwk.getAlgorithm();
            RSASSASigner jwsSigner = new RSASSASigner(secretKey);

            JWSHeader header = new JWSHeader.Builder(algorithm)
                    .keyID(jwk.getKeyID())
                    .build();

            List<String> authorities = userDetails.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();

            JWTClaimsSet claim = new JWTClaimsSet.Builder()
                    .subject("user")
                    .issuer("http://localhost:8080")
                    .claim("username", userDetails.getUsername())
                    .claim("authorities", authorities)
                    .expirationTime(new Date(new Date().getTime() + FIVE_MINUTE))
                    .build();

            SignedJWT signedJWT = new SignedJWT(header, claim);
            signedJWT.sign(jwsSigner);

            return signedJWT.serialize();

        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }
}
