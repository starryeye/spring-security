package dev.starryeye.production_ready_authorization_server.controller;

import dev.starryeye.production_ready_authorization_server.jpa.UserEntity;
import dev.starryeye.production_ready_authorization_server.jpa.UserEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class UserController {

    /**
     * 사용자 등록/조회용 admin API 이다. (ROLE_ADMIN 전용.. DefaultSecurityConfig 참고)
     *      client 등록(RegisteredClientController)과 마찬가지로 프레임워크가 제공하지 않는 기능이라 직접 만든다.
     *      실제 운영이라면 관리자 등록 외에 self-service 회원가입(이메일 검증, 비밀번호 정책 등)이 따로 있을 텐데..
     *      keycloak 대비 spring authorization server 로 직접 만들어야 하는 대표 영역이 바로 이 사용자 관리 층이다.
     */

    private final UserEntityRepository repository;

    // 기본 DelegatingPasswordEncoder.. encode 시 "{bcrypt}.." 로 저장되어 로그인 검증 시 prefix 로 인코더를 찾는다.
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @PostMapping("/users")
    public UserResponse register(@RequestBody RegisterUserRequest request) {

        if (repository.findByUsername(request.username()).isPresent()) {
            throw new IllegalArgumentException("username already exists: " + request.username());
        }

        UserEntity entity = UserEntity.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .authorities(StringUtils.collectionToCommaDelimitedString(request.authorities()))
                .enabled(true)
                .build();
        repository.save(entity);

        return UserResponse.from(entity);
    }

    @GetMapping("/users")
    public List<UserResponse> getUsers() {
        return repository.findAll().stream()
                .map(UserResponse::from)
                .toList();
    }
}
