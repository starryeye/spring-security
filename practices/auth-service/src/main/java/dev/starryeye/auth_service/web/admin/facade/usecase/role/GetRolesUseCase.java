package dev.starryeye.auth_service.web.admin.facade.usecase.role;

import dev.starryeye.auth_service.domain.MyRole;
import dev.starryeye.auth_service.web.admin.facade.response.RoleResponse;
import dev.starryeye.auth_service.web.admin.service.RoleQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetRolesUseCase {

    private final RoleQueryService roleQueryService;

    public List<RoleResponse> getRoles() {
        return roleQueryService.getAllRoles().stream()
                .map(RoleResponse::from)
                .toList();
    }

    public RoleResponse getRoleBy(Long id) {
        return RoleResponse.from(roleQueryService.getRole(id));
    }
}
