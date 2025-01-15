package dev.starryeye.auth_service.web.admin.facade.usecase.usermanagement;

import dev.starryeye.auth_service.domain.MyRole;
import dev.starryeye.auth_service.domain.MyUser;
import dev.starryeye.auth_service.domain.MyUserRole;
import dev.starryeye.auth_service.web.admin.facade.request.UpdateUserUseCaseRequest;
import dev.starryeye.auth_service.web.admin.service.RoleQueryService;
import dev.starryeye.auth_service.web.admin.service.UserManagementQueryService;
import dev.starryeye.auth_service.web.admin.service.UserManagementService;
import dev.starryeye.auth_service.web.admin.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional
public class ModifyUserUseCase {

    private final UserManagementQueryService userManagementQueryService;
    private final RoleQueryService roleQueryService;

    private final UserRoleService userRoleService;

    private final PasswordEncoder passwordEncoder;

    public void process(UpdateUserUseCaseRequest request) {

        MyUser user = userManagementQueryService.getUserWithRole(Long.parseLong(request.id()));

        List<MyRole> newRoles = roleQueryService.getAllRoles().stream()
                .filter(role -> request.roles().contains(role.getName().name()))
                .toList();
        List<MyUserRole> newUserRoles = MyUserRole.createUserRoles(user, newRoles);

        user.changeAge(request.age());
        user.changePassword(passwordEncoder.encode(request.password()));
        user.changeRoles(new HashSet<>(newUserRoles));
    }
}
