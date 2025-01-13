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

        /**
         * todo,
         *      cascade..
         *
         */

        MyUser user = userManagementQueryService.getUserWithRole(Long.parseLong(request.id()));

        user.changeAge(request.age());
        user.changePassword(passwordEncoder.encode(request.password()));

        userRoleService.deleteUserWithRoles(user.getRoles().stream().toList()); // todo, 삭제가 아니라 업데이트 느낌으로 해보기
        List<MyRole> newRoles = roleQueryService.getAllRoles().stream()
                .filter(role -> request.roles().contains(role.getName().name()))
                .toList();
        List<MyUserRole> userRoles = MyUserRole.createUserRoles(user, newRoles);
        userRoleService.createUserWithRoles(userRoles);
    }
}
