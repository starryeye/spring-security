package dev.starryeye.resource_server_comment.api.controller;

import dev.starryeye.resource_server_comment.api.controller.request.GetCommentsRequest;
import dev.starryeye.resource_server_comment.api.service.CommentService;
import dev.starryeye.resource_server_comment.dto.Comment;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/comments")
    public List<Comment> comments(@RequestBody GetCommentsRequest request) {
        return commentService.getCommentsBy(request.contentId());
    }
}
