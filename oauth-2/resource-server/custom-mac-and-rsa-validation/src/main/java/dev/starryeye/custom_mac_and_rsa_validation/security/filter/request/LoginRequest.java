package dev.starryeye.custom_mac_and_rsa_validation.security.filter.request;

public record LoginRequest(
        String username,
        String password
) {
}
