package dev.starryeye.token.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record IssuedRefreshToken(String refreshToken, long expiresAt, String familyId) {
}
