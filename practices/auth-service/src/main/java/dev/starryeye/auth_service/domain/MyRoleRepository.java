package dev.starryeye.auth_service.domain;

import dev.starryeye.auth_service.domain.type.MyRoleName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MyRoleRepository extends JpaRepository<MyRole, Long> {

    Optional<MyRole> findByName(MyRoleName name);
}
