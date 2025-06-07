package dev.starryeye.resource_server_article.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record Content(
        Long id,
        String title,
        String details,
        String owner,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,

        List<Comment> comments
) {
}
