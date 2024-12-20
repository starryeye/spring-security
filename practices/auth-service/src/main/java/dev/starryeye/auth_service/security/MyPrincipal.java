package dev.starryeye.auth_service.security;

import dev.starryeye.auth_service.domain.MyUser;

public record MyPrincipal(
        String username,
        String password,
        Integer age,
        String roles
) {

    public static MyPrincipal of(MyUser user) {
        return new MyPrincipal(user.getUsername(), user.getPassword(), user.getAge(), user.getRoles());
    }
}
