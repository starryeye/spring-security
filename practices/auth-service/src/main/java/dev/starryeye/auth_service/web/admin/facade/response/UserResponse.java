package dev.starryeye.auth_service.web.admin.facade.response;

import dev.starryeye.auth_service.domain.MyUser;

import java.util.List;

public record UserResponse(
        String id,
        String username,
        Integer age,
        String password,
        List<String> roles
) {

    public static UserResponse from(MyUser user) {
        return new UserResponse(
                user.getId().toString(),
                user.getUsername(),
                user.getAge(),
                user.getPassword(),
                user.getRoles().stream()
                        .map(myUserRole -> myUserRole.getMyRole().getName().name())
                        .toList()
        );
    }
}
