package dev.starryeye.auth_service.web.admin.facade.usecase.usermanagement;

import dev.starryeye.auth_service.web.admin.service.UserManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Transactional
public class DeleteUserUseCase {

    private final UserManagementService userManagementService;

    public void by(Long userId) {
        userManagementService.deleteUser(userId);
    }
}
