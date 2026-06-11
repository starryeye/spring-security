package dev.starryeye.hello_jpa_authorization_server.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RegisteredClientEntityRepository extends JpaRepository<RegisteredClientEntity, String> {

    Optional<RegisteredClientEntity> findByClientId(String clientId);
}
