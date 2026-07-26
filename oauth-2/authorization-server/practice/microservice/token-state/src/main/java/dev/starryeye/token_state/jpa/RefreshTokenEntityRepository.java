package dev.starryeye.token_state.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenEntityRepository extends JpaRepository<RefreshTokenEntity, Long> {

	/**
	 * 회전 경로 전용 조회다. PESSIMISTIC_WRITE 로 행을 잠가야 같은 토큰의 동시 요청이 직렬화된다.
	 *      잠금이 없으면 두 트랜잭션이 모두 ACTIVE 를 읽고 둘 다 회전에 성공하며,
	 *      새로 만드는 행의 token_hash 는 서로 다른 난수라 unique 제약에도 걸리지 않아 조용히 통과한다.
	 *      그러면 재사용 탐지가 아무것도 잡지 못한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from RefreshTokenEntity r where r.tokenHash = :tokenHash")
	Optional<RefreshTokenEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

	Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

	List<RefreshTokenEntity> findByFamilyId(String familyId);
}
