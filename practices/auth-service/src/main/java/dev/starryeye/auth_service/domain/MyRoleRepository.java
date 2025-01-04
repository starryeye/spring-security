package dev.starryeye.auth_service.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MyRoleRepository extends JpaRepository<MyRole, Long> {
}
