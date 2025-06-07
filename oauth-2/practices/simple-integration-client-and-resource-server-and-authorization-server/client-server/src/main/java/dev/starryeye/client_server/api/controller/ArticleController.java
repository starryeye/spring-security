package dev.starryeye.client_server.api.controller;

import dev.starryeye.client_server.dto.Article;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ArticleController {

    @GetMapping("/contents")
    public List<Article> contents() {
        return List.of();
    }

    @GetMapping("/content/{contentId}")
    public void content(@PathVariable String contentId) {
        //todo
    }
}
