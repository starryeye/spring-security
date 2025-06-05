package dev.starryeye.client_server.api.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ContentController {

    @GetMapping("/contents")
    public List<?> contents() {
        return List.of();
    }

    @GetMapping("/content/{contentId}")
    public void content(@PathVariable String contentId) {
        //todo
    }
}
