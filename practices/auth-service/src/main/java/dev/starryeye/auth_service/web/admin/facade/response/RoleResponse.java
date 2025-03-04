package dev.starryeye.auth_service.web.admin.facade.response;

import dev.starryeye.auth_service.domain.MyRole;

public record RoleResponse(
        String id,
        String roleName,
        String roleDesc,
        String isExpression
) {

    public static RoleResponse from(MyRole myRole) {

        return new RoleResponse(
                myRole.getId().toString(),
                myRole.getName(),
                myRole.getDescription(),
                myRole.getIsExpression().toString()
        );
    }

    public static RoleResponse empty() {
        return new RoleResponse(null, null, null, null);
    }
}
