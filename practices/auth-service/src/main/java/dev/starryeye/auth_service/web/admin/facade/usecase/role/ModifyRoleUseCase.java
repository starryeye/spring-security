package dev.starryeye.auth_service.web.admin.facade.usecase.role;

import dev.starryeye.auth_service.domain.MyRole;
import dev.starryeye.auth_service.web.admin.facade.request.ModifyRoleUseCaseRequest;
import dev.starryeye.auth_service.web.admin.service.RoleQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class ModifyRoleUseCase {

    private final RoleQueryService roleQueryService;

    public void process(ModifyRoleUseCaseRequest request) {

        MyRole role = roleQueryService.getRole(request.id());

        role.changeName(request.roleName());
        role.changeDescription(request.roleDesc());
        role.changeIsExpression(role.getIsExpression());
    }
}
