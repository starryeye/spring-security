package dev.starryeye.auth_service.web.api.service;

import dev.starryeye.auth_service.domain.MyUser;
import dev.starryeye.auth_service.domain.MyUserRepository;
import dev.starryeye.auth_service.web.api.service.request.RegisterUserServiceRequest;
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

        MyUser entity = MyUser.create(
                request.getUsername(),
                encodedPassword,
                request.getAge(),
                request.getRoles()
        );

        myUserRepository.save(entity);
    }
}
