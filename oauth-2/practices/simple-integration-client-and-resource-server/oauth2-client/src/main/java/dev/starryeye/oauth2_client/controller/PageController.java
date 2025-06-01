package dev.starryeye.oauth2_client.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/photo-viewer")
    public String photoViewer() {
        return "photo-viewer";
    }
}
