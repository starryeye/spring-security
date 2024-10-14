package dev.starryeye.custom_authorize_authorization_manager_2.way_2.controller;

import dev.starryeye.custom_authorize_authorization_manager_2.Account;
import dev.starryeye.custom_authorize_authorization_manager_2.way_2.service.HelloService2;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HelloController2 {

    private final HelloService2 helloService;

    @GetMapping("/user2")
    public String user(){
        return helloService.getUser();
    }

    @GetMapping("/account2")
    public Account account(String name){
        return helloService.getAccount(name);
    }

    @GetMapping("/display2")
    public String display(){
        return helloService.display();
    }
}
