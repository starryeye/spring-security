package dev.starryeye.audit.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventEntityRepository extends JpaRepository<AuditEventEntity, Long> {

	boolean existsByEventId(String eventId);
}
