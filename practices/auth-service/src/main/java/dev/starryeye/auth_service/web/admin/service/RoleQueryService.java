package dev.starryeye.auth_service.web.admin.service;

import dev.starryeye.auth_service.domain.MyRole;
import dev.starryeye.auth_service.domain.MyRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor

public class RoleQueryService {

    private final MyRoleRepository myRoleRepository;

    public MyRole getRole(Long id) {
        return myRoleRepository.findById(id)
                .orElseGet(() -> MyRole.builder().build());
    }

    public MyRole getRoleByName(String name) {
        return myRoleRepository.findByName(name)
                .orElseThrow(() -> new RuntimeException("Role 존재하지 않음.. role : " + name));
    }

    public List<MyRole> getAllRoles() {
        return myRoleRepository.findAll();
    }

    public List<MyRole> getAllRolesByIsExpression(boolean isExpression) {
        return myRoleRepository.findAllByIsExpression(isExpression);
    }
}
