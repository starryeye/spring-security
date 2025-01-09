package dev.starryeye.auth_service.web.admin.facade.usecase.usermanagement;

import dev.starryeye.auth_service.web.admin.facade.response.UserResponse;
import dev.starryeye.auth_service.web.admin.service.UserManagementQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GetUsersUseCase {

    private final UserManagementQueryService userManagementQueryService;

    public List<UserResponse> getUsers() {
        return userManagementQueryService.getAllUsers().stream()
                .map(UserResponse::from)
                .toList();
    }
}
