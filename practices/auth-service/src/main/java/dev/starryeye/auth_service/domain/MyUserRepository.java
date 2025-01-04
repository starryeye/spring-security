package dev.starryeye.auth_service.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MyUserRepository extends JpaRepository<MyUser, Long> {

//    @Query("""
//    select u
//    from MyUser u
//        join fetch u.roles ur
//        join fetch ur.myRole r
//    where u.username = :username
//    """)
    @EntityGraph(attributePaths = {"roles", "roles.myRole"})
    Optional<MyUser> findByUsername(@Param("username") String username);
}
