package dev.starryeye.client_server.dto;

public record ErrorResponse(
        String errorCode,
        String description
) {
}
