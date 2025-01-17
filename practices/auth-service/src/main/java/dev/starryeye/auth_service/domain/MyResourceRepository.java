package dev.starryeye.auth_service.domain;

import dev.starryeye.auth_service.domain.type.MyResourceType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MyResourceRepository extends JpaRepository<MyResource, Long> {

    List<MyResource> findAllByType(MyResourceType type, Sort sort);

    @EntityGraph(attributePaths = {"roles", "roles.myRole"})
    Optional<MyResource> findOneWithRolesById(Long id);

    // 아래는 메서드 이름 방식의 쿼리로 파싱하다가 예외가 터져서 직접 JPQL 을 사용하는 방식으로 변경함.
//    @EntityGraph(attributePaths = {"roles", "roles.myRole"})
//    List<MyResource> findAllWithRoles();

    @Query("""
    select r
    from MyResource r
        join fetch r.roles rr
        join fetch rr.myRole ro
    """)
    List<MyResource> findAllWithRoles();
}
