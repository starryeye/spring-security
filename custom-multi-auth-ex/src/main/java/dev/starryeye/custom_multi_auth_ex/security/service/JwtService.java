package dev.starryeye.custom_multi_auth_ex.security.service;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Date;

@Component
public class JwtService {

    private static final String SECRET = "thisIsASecretKeyThatIsAtLeast256BitsLong!";
    private static final JWSAlgorithm ALG = JWSAlgorithm.HS256;

    public String generateToken(String username) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(username)
                    .issueTime(new Date())
                    .expirationTime(Date.from(Instant.now().plusSeconds(3600))) // 1 hour
                    .build();

            JWSHeader header = new JWSHeader.Builder(ALG).type(JOSEObjectType.JWT).build();
            SignedJWT jwt = new SignedJWT(header, claims);

            jwt.sign(new MACSigner(SECRET.getBytes()));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException("JWT 생성 실패", e);
        }
    }

    public String validateTokenAndGetUsername(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(SECRET.getBytes()))) return null;

            Date now = new Date();
            Date expiration = jwt.getJWTClaimsSet().getExpirationTime();
            if (expiration != null && expiration.before(now)) return null;

            return jwt.getJWTClaimsSet().getSubject();
        } catch (Exception e) {
            return null;
        }
    }
}
