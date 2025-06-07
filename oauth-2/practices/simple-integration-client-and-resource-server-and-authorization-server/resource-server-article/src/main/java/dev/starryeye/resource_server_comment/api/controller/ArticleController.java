package dev.starryeye.resource_server_comment.api.controller;

import dev.starryeye.resource_server_comment.api.controller.request.GetArticlesRequest;
import dev.starryeye.resource_server_comment.api.service.ArticleService;
import dev.starryeye.resource_server_comment.dto.Comment;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @PostMapping("/comments")
    public List<Comment> comments(@RequestBody GetArticlesRequest request) {
        return articleService.getCommentsBy(request.contentId());
    }
}
