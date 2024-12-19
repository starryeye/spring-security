package dev.starryeye.auth_service.api.service;

import dev.starryeye.auth_service.api.service.request.RegisterUserServiceRequest;
import dev.starryeye.auth_service.domain.User;
import dev.starryeye.auth_service.domain.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final PasswordEncoder passwordEncoder;

    private final UserRepository userRepository;

    public void registerUser(RegisterUserServiceRequest request) {

        String encodedPassword = passwordEncoder.encode(request.getPassword());

        User entity = User.create(
                request.getIdentifier(),
                encodedPassword,
                request.getUsername(),
                request.getAge(),
                request.getRoles()
        );

        userRepository.save(entity);
    }
}
