package dev.starryeye.custom_mac_jwt_issuer_verifier.security.filter.username_password.request;

public record LoginRequest(
        String username,
        String password
) {
}
