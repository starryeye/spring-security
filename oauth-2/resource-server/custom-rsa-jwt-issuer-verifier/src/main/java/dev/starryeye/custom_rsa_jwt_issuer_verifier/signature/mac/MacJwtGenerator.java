package dev.starryeye.custom_rsa_jwt_issuer_verifier.signature.mac;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.starryeye.custom_rsa_jwt_issuer_verifier.signature.JwtClaim;
import dev.starryeye.custom_rsa_jwt_issuer_verifier.signature.JwtGenerator;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.List;

public class MacJwtGenerator implements JwtGenerator {

    private static final int FIVE_MINUTE = 60 * 1000 * 5;

    private final JWK jwk;

    private final JWSSigner jwsSigner;

    public MacJwtGenerator(JWK jwk) throws KeyLengthException {
        this.jwk = jwk;
        this.jwsSigner = new MACSigner(getSecretKey());
    }

    public String generateSignedToken(UserDetails userDetails) {

        SignedJWT unsignedToken = getUnsignedToken(userDetails);

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

    private SignedJWT getUnsignedToken(UserDetails userDetails) {
        JWSHeader header = new JWSHeader.Builder(getAlgorithm())
                .keyID(jwk.getKeyID())
                .build();

        List<String> authorities = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        JWTClaimsSet claim = new JWTClaimsSet.Builder()
                .subject("user")
                .issuer("http://localhost:8080")
                .claim(JwtClaim.CLAIM_USERNAME.getClaimName(), userDetails.getUsername())
                .claim(JwtClaim.CLAIM_AUTHORITIES.getClaimName(), authorities)
                .expirationTime(new Date(new Date().getTime() + FIVE_MINUTE))
                .build();

        return new SignedJWT(header, claim);
    }

    private SecretKey getSecretKey() {
        return jwk.toOctetSequenceKey().toSecretKey();
    }

    private JWSAlgorithm getAlgorithm() {
        return (JWSAlgorithm) jwk.getAlgorithm();
    }
}
