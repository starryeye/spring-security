package dev.starryeye.user_directory;

import dev.starryeye.user_directory.jpa.UserEntity;
import dev.starryeye.user_directory.jpa.UserEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserSeedInitializer implements ApplicationRunner {

	/**
	 * 첫 슬라이스는 admin 등록 API 없이 seed 로 사용자 하나를 넣는다. (이후 슬라이스에서 등록 API)
	 */

	private final UserEntityRepository repository;

	@Override
	public void run(ApplicationArguments args) {
		if (repository.findByUsername("user").isPresent()) {
			return;
		}
		repository.save(UserEntity.builder()
				.sub("user-sub-0001")
				.username("user")
				.password(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("1111"))
				.authorities("ROLE_USER")
				.build());
	}
}
