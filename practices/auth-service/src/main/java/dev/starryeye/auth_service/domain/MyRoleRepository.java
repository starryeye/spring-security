package dev.starryeye.auth_service.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MyRoleRepository extends JpaRepository<MyRole, Long> {

    Optional<MyRole> findByName(String name);

    List<MyRole> findAllByIsExpression(Boolean isExpression);
}
