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

    public static Comment ofDefault(Long contentId) {
        return new Comment(null, "system","댓글이 없습니다.", null, null, contentId);
    }
}
