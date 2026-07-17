package dev.starryeye.resource_server_article.dto;

public record ErrorResponse(
        String errorCode,
        String description
) {
}
