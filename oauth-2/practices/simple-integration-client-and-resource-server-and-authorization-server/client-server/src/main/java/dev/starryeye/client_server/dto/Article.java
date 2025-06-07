package dev.starryeye.client_server.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record Article(
        Long id,
        String title,
        String details,
        String owner,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,

        List<Comment> comments
) {
}
