package dev.starryeye.token.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RefreshTokenInfo(boolean active, String sub, String clientId, String scope, long exp, long iat) {
}
