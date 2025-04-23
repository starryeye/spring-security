package dev.starryeye.custom_mac_jwt_issuer_verifier.signature;

import org.springframework.security.core.userdetails.UserDetails;

public interface JwtGenerator {

    String generateSignedToken(UserDetails userDetails);
}