package dev.starryeye.client_server.dto;

import dev.starryeye.client_server.dto.element.Comment;
import dev.starryeye.client_server.dto.element.Content;

public record Article(
        Long contentId,
        Content content,
        Comment comment
) {
}
