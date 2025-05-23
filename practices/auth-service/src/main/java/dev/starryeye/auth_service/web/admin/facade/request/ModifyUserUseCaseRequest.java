package dev.starryeye.auth_service.web.admin.facade.request;

import java.util.List;

public record ModifyUserUseCaseRequest(
        Long id,
        String username,
        Integer age,
        String password,
        List<String> roles
) {
}
