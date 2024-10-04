package dev.starryeye.custom_authorize_request_matcher;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/resources")
    public String resources() {
        return "resources";
    }

    @GetMapping("/admin/db")
    public String db() {
        return "admin db";
    }
}
