package dev.starryeye.custom_authorize_method_interceptor.service;

import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class HelloService {

    @PreAuthorize(value = "")
    public String getUser() {
        return "user";
    }

    @PostAuthorize(value = "")
    public Account getAccount(String username) {
        return new Account(username, false);
    }

    public String display() {
        return "display";
    }
}
