package dev.starryeye.auth_service.domain;

import dev.starryeye.auth_service.domain.type.MyResourceType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MyResourceRepository extends JpaRepository<MyResource, Long> {

    List<MyResource> findAllByType(MyResourceType type, Sort sort);

    @EntityGraph(attributePaths = {"roles", "roles.myRole"})
    Optional<MyResource> findOneWithRolesById(Long id);
}
