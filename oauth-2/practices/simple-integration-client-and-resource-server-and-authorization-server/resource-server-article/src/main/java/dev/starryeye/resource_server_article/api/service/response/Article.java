package dev.starryeye.resource_server_article.api.service.response;

import dev.starryeye.resource_server_article.dto.Comment;
import dev.starryeye.resource_server_article.dto.Content;

public record Article(
        Long contentId,
        Content content,
        Comment comment
) {
}
