package dev.starryeye.consent.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsentEntityRepository extends JpaRepository<ConsentEntity, Long> {

	Optional<ConsentEntity> findBySubAndClientId(String sub, String clientId);
}
