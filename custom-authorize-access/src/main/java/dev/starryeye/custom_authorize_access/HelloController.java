package dev.starryeye.custom_authorize_access;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/resources")
    public String resources() {
        return "resources";
    }

    @GetMapping("/users/{name}")
    public String hello(@PathVariable String name) {
        return "Hello " + name;
    }

    @GetMapping("/admin/db")
    public String db() {
        return "admin db";
    }
}
