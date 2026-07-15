package dev.starryeye.production_ready_authorization_server.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OAuth2AuthorizationConsentEntityRepository extends JpaRepository<OAuth2AuthorizationConsentEntity, OAuth2AuthorizationConsentEntity.CompositeId> {
}
