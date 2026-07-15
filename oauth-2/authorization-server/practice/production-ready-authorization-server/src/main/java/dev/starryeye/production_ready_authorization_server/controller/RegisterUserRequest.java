package dev.starryeye.production_ready_authorization_server.controller;

import java.util.List;

public record RegisterUserRequest(
        String username,
        String password, // raw 로 받아 서버가 bcrypt 인코딩하여 저장한다.
        List<String> authorities // "ROLE_" prefix 포함 (예. ["ROLE_USER", "ROLE_CUSTOMER"])
) {
}
