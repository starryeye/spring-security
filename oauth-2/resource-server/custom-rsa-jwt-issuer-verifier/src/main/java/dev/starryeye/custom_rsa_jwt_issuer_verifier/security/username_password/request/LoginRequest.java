package dev.starryeye.custom_rsa_jwt_issuer_verifier.security.username_password.request;

public record LoginRequest(
        String username,
        String password
) {
}
