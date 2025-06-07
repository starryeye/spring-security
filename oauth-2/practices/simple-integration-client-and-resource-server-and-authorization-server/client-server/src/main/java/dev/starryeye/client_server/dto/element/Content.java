package dev.starryeye.client_server.dto.element;

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
