package dev.starryeye.auth_service.domain;

import dev.starryeye.auth_service.domain.type.MyResourceType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MyResourceRepository extends JpaRepository<MyResource, Long> {

    @EntityGraph(attributePaths = "roles")
    List<MyResource> findResourcesByType(MyResourceType type, Sort sort);
}
