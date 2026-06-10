package dev.starryeye.client_server.dto;

import dev.starryeye.client_server.dto.element.Comment;
import dev.starryeye.client_server.dto.element.Content;

import java.util.List;

public record Article(
        Long id,
        Content content,
        List<Comment> comment
) {
}
