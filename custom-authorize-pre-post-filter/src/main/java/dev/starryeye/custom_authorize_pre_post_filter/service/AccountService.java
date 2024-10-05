package dev.starryeye.custom_authorize_pre_post_filter.service;

import dev.starryeye.custom_authorize_pre_post_filter.controller.Account;
import org.springframework.security.access.prepost.PostFilter;
import org.springframework.security.access.prepost.PreFilter;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AccountService {

    /**
     * @PreFilter, @PostFilter
     * - @EnableMethodSecurity(prePostEnabled = true) 설정에 의해 활성화되는 기능이다.
     *
     * @PreFilter
     * - 메서드 수행 전, 컬렉션 파라미터에 대해 어노테이션 조건으로 필터링을 수행하고 실제 메서드를 수행한다.
     *
     * @PostFilter
     * - 메서드 수행 이후, 반환 컬랙션 객체에 대해 어노테이션 조건으로 필터링을 수행하고 실제 반환된다.
     */

    @PreFilter("filterObject.owner() == authentication.name")
    public List<Account> writeList(List<Account> accounts) {
        return accounts;
    }
    @PreFilter("filterObject.value.owner() == authentication.name")
    public Map<String, Account> writeMap(Map<String, Account> accountsMappedToOwner) {
        return accountsMappedToOwner;
    }

    @PostFilter("filterObject.owner() == authentication.name")
    public List<Account> readList() {
        return new ArrayList<>(List.of(
                new Account("user",false),
                new Account("db",false),
                new Account("admin",false)
        ));
    }
    @PostFilter("filterObject.value.owner() == authentication.name")
    public Map<String, Account> readMap() {
        return new HashMap<>(Map.of("user", new Account("user", false),
                "db", new Account("db", false),
                "admin", new Account("admin", false)));
    }
}
