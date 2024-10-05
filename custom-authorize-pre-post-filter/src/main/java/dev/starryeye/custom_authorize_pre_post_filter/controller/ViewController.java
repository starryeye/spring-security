package dev.starryeye.custom_authorize_pre_post_filter.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping("/example")
    public String example() {
        return "example";
    }
}
