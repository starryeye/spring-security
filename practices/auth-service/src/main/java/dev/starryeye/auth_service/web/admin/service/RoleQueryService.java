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
        return this.myRoleRepository.findById(id)
                .orElseGet(() -> MyRole.builder().build());
    }

    public List<MyRole> getAllRoles() {
        return this.myRoleRepository.findAll();
    }

    public List<MyRole> getAllRolesByIsExpression(boolean isExpression) {
        return myRoleRepository.findAllByIsExpression(isExpression);
    }
}
