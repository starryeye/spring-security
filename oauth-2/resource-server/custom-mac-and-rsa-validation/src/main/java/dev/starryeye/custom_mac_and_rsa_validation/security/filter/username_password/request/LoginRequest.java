package dev.starryeye.custom_mac_and_rsa_validation.security.filter.username_password.request;

public record LoginRequest(
        String username,
        String password
) {
}
