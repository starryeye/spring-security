package dev.starryeye.custom_authorize_authorization_manager_2.way_2.service;

import dev.starryeye.custom_authorize_authorization_manager_2.Account;
import org.springframework.stereotype.Service;

@Service
public class HelloService2 {

    public String getUser() {
        return "user";
    }

    public Account getAccount(String username) {
        return new Account(username, false);
    }

    public String display() {
        return "display";
    }
}
