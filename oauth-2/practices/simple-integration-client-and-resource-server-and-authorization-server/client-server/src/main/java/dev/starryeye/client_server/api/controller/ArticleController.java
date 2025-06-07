package dev.starryeye.client_server.api.controller;

import dev.starryeye.client_server.api.service.ArticleService;
import dev.starryeye.client_server.dto.Article;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping("/articles")
    public List<Article> articles() {
        return List.of();
    }

    @GetMapping("/article/{articleId}")
    public Article content(@PathVariable Long articleId) {
        return articleService.getArticle(articleId);
    }
}
