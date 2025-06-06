package dev.starryeye.resource_server_article.dto;

import java.time.OffsetDateTime;

public record Comment(
        Long id,
        String author,
        String details,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,

        Long contentId
) {
}
