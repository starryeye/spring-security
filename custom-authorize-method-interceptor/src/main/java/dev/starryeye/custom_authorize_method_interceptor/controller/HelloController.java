package dev.starryeye.custom_authorize_method_interceptor.controller;

import dev.starryeye.custom_authorize_method_interceptor.service.Account;
import dev.starryeye.custom_authorize_method_interceptor.service.HelloService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HelloController {

    private final HelloService helloService;

    @GetMapping("/user")
    public String user(){
        return helloService.getUser();
    }

    @GetMapping("/account")
    public Account account(String name){
        return helloService.getAccount(name);
    }

    @GetMapping("/display")
    public String display(){
        return helloService.display();
    }
}
