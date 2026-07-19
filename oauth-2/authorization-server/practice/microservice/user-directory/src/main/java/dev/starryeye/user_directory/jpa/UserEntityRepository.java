package dev.starryeye.user_directory.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserEntityRepository extends JpaRepository<UserEntity, Long> {
	Optional<UserEntity> findByUsername(String username);
	Optional<UserEntity> findBySub(String sub);
}
