package dev.starryeye.custom_rsa_jwt_issuer_verifier.signature;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.text.ParseException;
import java.util.List;
import java.util.UUID;

public class JwtVerifier {

    private static final String CLAIM_USERNAME = "username";
    private static final String CLAIM_AUTHORITY = "authorities";

    // todo, refactoring
    private final RSASSAVerifier verifier;

    public JwtVerifier(JWK jwk) throws JOSEException {
        this.verifier = new RSASSAVerifier(jwk.toRSAKey().toRSAPublicKey());
    }

    public UserDetails verify(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            if (!signedJWT.verify(verifier)) {
                throw new BadCredentialsException("Invalid signature for token: " + token);
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            String username = claims.getStringClaim(CLAIM_USERNAME);
            List<String> authorities = claims.getStringListClaim(CLAIM_AUTHORITY);

            if (username == null || authorities == null || authorities.isEmpty()) {
                throw new BadCredentialsException("Missing required claims in token");
            }

            return User.withUsername(username)
                    .password(UUID.randomUUID().toString()) // password placeholder
                    .authorities(authorities.toArray(new String[0]))
                    .build();

        } catch (ParseException | JOSEException e) {
            throw new BadCredentialsException("Token parsing failed: " + token, e);
        }
    }
}
