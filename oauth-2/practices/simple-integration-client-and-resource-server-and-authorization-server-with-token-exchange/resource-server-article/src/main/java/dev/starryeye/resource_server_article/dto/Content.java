package dev.starryeye.resource_server_article.dto;

import java.time.OffsetDateTime;

public record Content(
        Long id,
        String title,
        String details,
        String owner,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
