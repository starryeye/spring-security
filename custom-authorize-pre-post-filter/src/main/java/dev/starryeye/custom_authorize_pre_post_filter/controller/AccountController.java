package dev.starryeye.custom_authorize_pre_post_filter.controller;

import dev.starryeye.custom_authorize_pre_post_filter.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/writeList")
    public List<Account> writeList(@RequestBody List<Account> accounts) {

        return accountService.writeList(accounts);
    }

    @PostMapping("/writeMap")
    public Map<String, Account> writeMap(@RequestBody List<Account> accounts) {

        Map<String, Account> dataMap = accounts.stream()
                .collect(Collectors.toMap(Account::owner, account -> account));

        return accountService.writeMap(dataMap);
    }

    @GetMapping("/readList")
    public List<Account> readList() {

        return accountService.readList();
    }

    @GetMapping("/readMap")
    public Map<String, Account> readMap() {

        return accountService.readMap();
    }
}
