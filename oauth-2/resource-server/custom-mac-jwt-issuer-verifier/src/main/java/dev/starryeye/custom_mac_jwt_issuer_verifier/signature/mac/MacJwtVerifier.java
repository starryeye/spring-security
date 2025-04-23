package dev.starryeye.custom_mac_jwt_issuer_verifier.signature.mac;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.starryeye.custom_mac_jwt_issuer_verifier.signature.JwtClaim;
import dev.starryeye.custom_mac_jwt_issuer_verifier.signature.JwtVerifier;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.text.ParseException;
import java.util.List;
import java.util.UUID;

public class MacJwtVerifier implements JwtVerifier {

    private final JWSVerifier jwsVerifier;

    public MacJwtVerifier(JWK jwk) throws JOSEException {
        this.jwsVerifier = new MACVerifier(jwk.toOctetSequenceKey().toSecretKey());
    }

    public UserDetails verify(String token) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            if (!signedJWT.verify(this.jwsVerifier)) {
                throw new BadCredentialsException("Invalid signature for token: " + token);
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            String username = claims.getStringClaim(JwtClaim.CLAIM_USERNAME.getClaimName());
            List<String> authorities = claims.getStringListClaim(JwtClaim.CLAIM_AUTHORITIES.getClaimName());

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
