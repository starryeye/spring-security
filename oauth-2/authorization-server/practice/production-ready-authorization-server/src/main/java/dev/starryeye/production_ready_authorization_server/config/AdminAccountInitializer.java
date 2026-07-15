package dev.starryeye.production_ready_authorization_server.config;

import dev.starryeye.production_ready_authorization_server.jpa.UserEntity;
import dev.starryeye.production_ready_authorization_server.jpa.UserEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    /**
     * 최초 관리자 계정 부트스트랩이다.
     *      client 와 사용자는 seed 없이 admin API 로 등록하는 것이 이 프로젝트의 원칙이지만..
     *      그 API 를 호출할 관리자(ROLE_ADMIN)가 최소 1명은 있어야 하므로(닭과 달걀) 최초 관리자만 부팅 시 생성한다.
     *      keycloak 이 최초 admin 을 환경변수(KEYCLOAK_ADMIN)로 만드는 것과 같은 이유의 장치다.
     *
     * 이미 존재하면 건너뛰므로 재기동에서 멱등이다.
     *      다중 인스턴스 "동시" 기동은 존재 확인만으로 부족하다.. (실제 겪은 경합)
     *      두 인스턴스가 거의 동시에 "없음" 을 확인하고 둘 다 insert 하여 한쪽이 unique 제약 위반이 났는데..
     *      ApplicationRunner 의 예외는 애플리케이션 기동 실패로 이어져 인스턴스가 통째로 내려가버렸다.
     *      -> unique 제약 위반(DataIntegrityViolationException)은 "다른 인스턴스가 먼저 만들었다" 는 뜻이므로 잡아서 건너뛴다.
     */

    private final UserEntityRepository repository;
    private final String username;
    private final String password;

    public AdminAccountInitializer(
            UserEntityRepository repository,
            @Value("${my.bootstrap-admin.username}") String username,
            @Value("${my.bootstrap-admin.password}") String password
    ) {
        this.repository = repository;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {

        if (repository.findByUsername(username).isPresent()) {
            log.info("[bootstrap] 관리자 계정이 이미 존재하여 생성을 건너뛴다. username={}", username);
            return;
        }

        try {
            repository.save(UserEntity.builder()
                    .username(username)
                    .password(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(password))
                    .authorities("ROLE_ADMIN")
                    .enabled(true)
                    .build());
            log.info("[bootstrap] 최초 관리자 계정을 생성했다. username={}", username);
        } catch (DataIntegrityViolationException e) {
            log.info("[bootstrap] 동시에 기동한 다른 인스턴스가 먼저 관리자 계정을 생성하여 건너뛴다. username={}", username);
        }
    }
}
