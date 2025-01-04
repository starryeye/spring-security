package dev.starryeye.auth_service.security;

import dev.starryeye.auth_service.domain.MyRole;
import dev.starryeye.auth_service.domain.MyUser;
import dev.starryeye.auth_service.domain.MyUserRole;
import dev.starryeye.auth_service.domain.type.MyRoleName;

import java.util.List;

public record MyPrincipal(
        String username,
        String password,
        Integer age,
        List<String> roles
) {

    public static MyPrincipal of(MyUser user) {
        return new MyPrincipal(
                user.getUsername(),
                user.getPassword(),
                user.getAge(),
                user.getRoles().stream()
                        .map(MyUserRole::getMyRole)
                        .map(MyRole::getName)
                        .map(Enum::name)
                        .toList()
        );
    }
}
