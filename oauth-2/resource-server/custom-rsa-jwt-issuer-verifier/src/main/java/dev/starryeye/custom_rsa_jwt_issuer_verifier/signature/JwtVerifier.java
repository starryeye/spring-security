package dev.starryeye.custom_rsa_jwt_issuer_verifier.signature;

import org.springframework.security.core.userdetails.UserDetails;

public interface JwtVerifier {

    UserDetails verify(String token);
}
