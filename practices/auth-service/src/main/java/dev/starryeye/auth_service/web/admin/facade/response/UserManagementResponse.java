package dev.starryeye.auth_service.web.admin.facade.response;

import java.util.List;

public record UserManagementResponse(
        UserResponse userResponse,
        List<RoleResponse> roles
) {
}
