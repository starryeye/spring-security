package dev.starryeye.session.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OidcSessionEntityRepository extends JpaRepository<OidcSessionEntity, Long> {

	List<OidcSessionEntity> findBySid(String sid);

	boolean existsBySidAndClientId(String sid, String clientId);

	void deleteBySid(String sid);
}
