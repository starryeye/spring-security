package dev.starryeye.resource_server_article.api.service.response;

import dev.starryeye.resource_server_article.dto.Comment;
import dev.starryeye.resource_server_article.dto.Content;

import java.util.List;

public record Article(
        Long id,
        Content content,
        List<Comment> comment
) {
}
