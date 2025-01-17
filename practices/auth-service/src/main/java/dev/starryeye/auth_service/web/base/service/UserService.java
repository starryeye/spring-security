package dev.starryeye.auth_service.web.base.service;

import dev.starryeye.auth_service.domain.*;
import dev.starryeye.auth_service.web.base.service.request.RegisterUserServiceRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final PasswordEncoder passwordEncoder;

    private final MyUserRepository myUserRepository;
    private final MyRoleRepository myRoleRepository;
    private final MyUserRoleRepository myUserRoleRepository;

    public void registerUser(RegisterUserServiceRequest request) {

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        /**
         * TODO
         *  1. Role 을 찾아서 있는 경우에만 유저를 등록한다.(없으면 에러처리)
         *  2. MyRole entity 가 MyUser(aggregate root) 의 aggregate 에 속하므로..
         *      Role 을 찾는 과정을 MyUser.createUser 로 묶으면 좋을 것 같은데..
         *  3. cascade 를 이용해보자..
         */

        MyRole role = myRoleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new RuntimeException("Role 존재하지 않음.. role : ROLE_USER"));

        MyUser user = MyUser.create(
                request.getUsername(),
                encodedPassword,
                request.getAge()
        );
        myUserRepository.save(user);

        MyUserRole userRole = MyUserRole.create(user, role);
        myUserRoleRepository.save(userRole);
    }
}
