package dev.starryeye.auth_service.web.admin.service;

import dev.starryeye.auth_service.domain.MyRole;
import dev.starryeye.auth_service.domain.MyRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final MyRoleRepository myRoleRepository;

    public List<MyRole> getAllRoles() {
        return this.myRoleRepository.findAll();
    }
}
