package dev.starryeye.custom_rsa_jwt_issuer_verifier.signature;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.List;

public class JwtGenerator {

    private static final int FIVE_MINUTE = 60 * 1000 * 5;

    private final JWK jwk;

    private final JWSSigner jwsSigner;

    public JwtGenerator(JWK jwk) {
        this.jwk = jwk;
        this.jwsSigner = new RSASSASigner(getPrivateKey());
    }

    public String generateSignedToken(UserDetails userDetails) {

        SignedJWT unsignedToken = generateUnsignedToken(userDetails);

        SignedJWT signedToken = sign(unsignedToken);

        return signedToken.serialize();

    }

    private SignedJWT sign(SignedJWT token) {
        try {
            token.sign(this.jwsSigner);
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
        return token;
    }

    private SignedJWT generateUnsignedToken(UserDetails userDetails) {
        JWSHeader header = new JWSHeader.Builder(getAlgorithm())
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

        return new SignedJWT(header, claim);
    }

    private RSAPrivateKey getPrivateKey() {
        try {
            return this.jwk.toRSAKey().toRSAPrivateKey();
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }

    private JWSAlgorithm getAlgorithm() {
        return (JWSAlgorithm) this.jwk.getAlgorithm();
    }
}
