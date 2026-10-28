package dev.starryeye.audit.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RefreshTokenEventEntityRepository extends JpaRepository<RefreshTokenEventEntity, Long> {

	boolean existsByEventId(String eventId);

	/**
	 * 한 sub 의 사건을 발생 시각 오름차순으로, 같은 시각이면 기록된 순서(id)로 돌려준다.
	 *
	 * 주의. 한 sub 의 사건을 전부 읽는다. 페이지를 나누지 않으므로 한 사용자의 사건이 많아지면 그만큼 한 번에 읽힌다.
	 */
	List<RefreshTokenEventEntity> findBySubOrderByOccurredAtAscIdAsc(String sub);
}
