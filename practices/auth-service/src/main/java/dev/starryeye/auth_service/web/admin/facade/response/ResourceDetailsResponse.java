package dev.starryeye.auth_service.web.admin.facade.response;

import java.util.List;

public record ResourceDetailsResponse(
        List<RoleResponse> allRoles,
        List<String> roleNamesOfResource,
        ResourceResponse resource
) {
}
