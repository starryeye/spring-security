package dev.starryeye.auth_service.web.admin.service;

import dev.starryeye.auth_service.domain.MyRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final MyRoleRepository myRoleRepository;
}
