package dev.starryeye.auth_service.web.admin.facade.usecase.usermanagement;

import dev.starryeye.auth_service.web.admin.facade.response.RoleResponse;
import dev.starryeye.auth_service.web.admin.facade.response.UserManagementResponse;
import dev.starryeye.auth_service.web.admin.facade.response.UserResponse;
import dev.starryeye.auth_service.web.admin.service.RoleQueryService;
import dev.starryeye.auth_service.web.admin.service.UserManagementQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetUserManagementUseCase {

    private final UserManagementQueryService userManagementQueryService;
    private final RoleQueryService roleQueryService;

    public UserManagementResponse getUserManagementBy(Long userId) {

        UserResponse userResponse = UserResponse.from(userManagementQueryService.getUserWithRole(userId));

        List<RoleResponse> roleResponses = roleQueryService.getAllRolesByIsExpression(false).stream()
                .map(RoleResponse::from)
                .toList();

        return new UserManagementResponse(userResponse, roleResponses);
    }
}
