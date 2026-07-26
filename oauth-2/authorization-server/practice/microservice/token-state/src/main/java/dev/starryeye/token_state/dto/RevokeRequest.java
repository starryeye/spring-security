package dev.starryeye.token_state.dto;

public record RevokeRequest(String refreshToken, String clientId) {
}
