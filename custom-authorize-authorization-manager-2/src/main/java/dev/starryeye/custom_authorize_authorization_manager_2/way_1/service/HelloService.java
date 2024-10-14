package dev.starryeye.custom_authorize_authorization_manager_2.way_1.service;

import dev.starryeye.custom_authorize_authorization_manager_2.Account;
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