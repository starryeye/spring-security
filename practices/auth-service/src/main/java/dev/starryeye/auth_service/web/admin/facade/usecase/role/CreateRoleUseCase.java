package dev.starryeye.auth_service.web.admin.facade.usecase.role;

import dev.starryeye.auth_service.domain.MyRole;
import dev.starryeye.auth_service.domain.type.MyRoleName;
import dev.starryeye.auth_service.web.admin.facade.request.CreateRoleUseCaseRequest;
import dev.starryeye.auth_service.web.admin.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CreateRoleUseCase {

    private final RoleService roleService;

    public void process(CreateRoleUseCaseRequest request) {

        MyRole role = MyRole.create(
                MyRoleName.fromString(request.roleName()), // todo, 권한명은 동적으로 생성될 수 있으므로 enum 으로 관리 X
                request.roleDesc(),
                toBoolean(request.isExpression())
        );

        roleService.createRole(role);
    }

    public static boolean toBoolean(String str) {

        return Optional.ofNullable(str)
                .map(s -> {
                    if ("Y".equalsIgnoreCase(s) || "true".equalsIgnoreCase(s)) return true;
                    if ("N".equalsIgnoreCase(s) || "false".equalsIgnoreCase(s)) return false;
                    throw new IllegalArgumentException("isExpression.. invalid argument : " + s);
                })
                .orElse(false);
    }
}