package dev.starryeye.resource_server_article.api.controller;

import dev.starryeye.resource_server_article.api.service.ArticleService;
import dev.starryeye.resource_server_article.api.service.response.Article;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping("/articles")
    public List<Article> articles() {
        return List.of();
    }

    @GetMapping("/articles/{id}")
    public Article article(@PathVariable Long id) {
        return articleService.getArticleBy(id);
    }
}
