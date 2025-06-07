package dev.starryeye.resource_server_article.api.controller;

import dev.starryeye.resource_server_article.dto.Content;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ContentController {

    @GetMapping("/contents")
    public List<Content> contents() {
        return List.of();
    }
}
