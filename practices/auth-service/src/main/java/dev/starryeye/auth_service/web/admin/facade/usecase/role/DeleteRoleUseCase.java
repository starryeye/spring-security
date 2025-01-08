package dev.starryeye.auth_service.web.admin.facade.usecase.role;

import dev.starryeye.auth_service.web.admin.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DeleteRoleUseCase {

    private final RoleService roleService;

    public void process(Long roleId) {
        roleService.deleteRole(roleId);
    }
}
