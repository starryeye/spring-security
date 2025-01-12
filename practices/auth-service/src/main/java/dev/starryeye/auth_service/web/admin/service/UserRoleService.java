package dev.starryeye.auth_service.web.admin.service;

import dev.starryeye.auth_service.domain.MyUserRole;
import dev.starryeye.auth_service.domain.MyUserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class UserRoleService {

    private final MyUserRoleRepository myUserRoleRepository;

    public void createUserWithRoles(List<MyUserRole> myUserRoles) {
        myUserRoleRepository.saveAll(myUserRoles);
    }

    public void deleteUserWithRoles(List<MyUserRole> myUserRoles) {
        myUserRoleRepository.deleteAll(myUserRoles);
    }
}
