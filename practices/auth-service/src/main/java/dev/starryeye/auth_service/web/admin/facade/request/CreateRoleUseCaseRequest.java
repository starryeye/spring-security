package dev.starryeye.auth_service.web.admin.facade.request;

public record CreateRoleUseCaseRequest(
        String roleName,
        String roleDesc,
        String isExpression
) {
}
