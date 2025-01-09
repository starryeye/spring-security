package dev.starryeye.auth_service.web.admin.service;

import dev.starryeye.auth_service.domain.MyUser;
import dev.starryeye.auth_service.domain.MyUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserManagementQueryService {

    private final MyUserRepository myUserRepository;

    public MyUser getUserWithRole(Long userId) {

        return myUserRepository.findOneWithRolesById(userId)
                .orElseGet(() -> MyUser.builder().build());
    }

    public List<MyUser> getAllUsers() {
        return myUserRepository.findAll();
    }
}
