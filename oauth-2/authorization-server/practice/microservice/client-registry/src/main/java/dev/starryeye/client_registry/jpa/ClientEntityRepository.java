package dev.starryeye.client_registry.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientEntityRepository extends JpaRepository<ClientEntity, String> {
}
