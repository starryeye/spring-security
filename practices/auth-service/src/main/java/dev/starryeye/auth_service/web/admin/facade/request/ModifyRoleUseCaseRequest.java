package dev.starryeye.auth_service.web.admin.facade.request;

public record ModifyRoleUseCaseRequest(
        Long id,
        String roleName,
        String roleDesc,
        String isExpression
) {
}
