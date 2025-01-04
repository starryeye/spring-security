package dev.starryeye.auth_service.web.base.service;

import dev.starryeye.auth_service.domain.MyUser;
import dev.starryeye.auth_service.domain.MyUserRepository;
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

    public void registerUser(RegisterUserServiceRequest request) {

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        /**
         * TODO
         *  Role 을 찾아서 있는 경우에만 유저를 등록한다.(없으면 에러처리)
         */
        MyUser entity = MyUser.create(
                request.getUsername(),
                encodedPassword,
                request.getAge(),
                request.getRoles()
        );

        myUserRepository.save(entity);
    }
}
