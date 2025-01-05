package dev.starryeye.auth_service.web.admin.facade.response;

import java.util.List;

public record ResourceDetailsResponse(
        String name,
        String type,
        String httpMethod,
        String orderNumber,

        List<RoleResponse> roles
) {
}
