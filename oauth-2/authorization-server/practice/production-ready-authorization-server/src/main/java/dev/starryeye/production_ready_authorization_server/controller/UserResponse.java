package dev.starryeye.production_ready_authorization_server.controller;

import dev.starryeye.production_ready_authorization_server.jpa.UserEntity;
import org.springframework.util.StringUtils;

import java.util.List;

public record UserResponse(
        String username,
        List<String> authorities,
        boolean enabled
        // password 는 인코딩된 값이라도 응답에 노출하지 않는다.
) {

    public static UserResponse from(UserEntity entity) {
        return new UserResponse(
                entity.getUsername(),
                List.of(StringUtils.commaDelimitedListToStringArray(entity.getAuthorities())),
                entity.isEnabled()
        );
    }
}
