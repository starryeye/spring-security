package dev.starryeye.token.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record TokenResponse(String access_token, String token_type, long expires_in, String scope, String id_token) {
}
