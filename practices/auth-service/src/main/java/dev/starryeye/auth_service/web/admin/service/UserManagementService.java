package dev.starryeye.auth_service.web.admin.service;

import dev.starryeye.auth_service.domain.MyUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserManagementService {

    private final MyUserRepository myUserRepository;

    public void deleteUser(Long userId) {
        myUserRepository.deleteById(userId);
    }
}
