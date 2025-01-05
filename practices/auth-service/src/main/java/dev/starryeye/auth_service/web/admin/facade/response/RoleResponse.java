package dev.starryeye.auth_service.web.admin.facade.response;

public record RoleResponse(
        String name,
        String description,
        String isExpression
) {
}
