package dev.starryeye.auth_service.web.admin.service;

import dev.starryeye.auth_service.domain.MyRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class RoleService {

    private final MyRoleRepository myRoleRepository;

    public void deleteRole(Long roleId) {
        myRoleRepository.deleteById(roleId);
    }
}

