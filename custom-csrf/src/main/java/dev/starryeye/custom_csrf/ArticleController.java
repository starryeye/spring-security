package dev.starryeye.custom_csrf;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/articles")
public class ArticleController {

    @GetMapping
    public String articles() {

        return "{\"title\":\"starry eye's article\"}";
    }

    @PostMapping("/new")
    public String newUser(@RequestBody CreateArticleRequest articleRequest) {

        return "Success created";
    }
}
