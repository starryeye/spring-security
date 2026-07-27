package dev.starryeye.token_state.jpa;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenEntityRepository extends JpaRepository<RefreshTokenEntity, Long> {

	Optional<RefreshTokenEntity> findByTokenHash(String tokenHash);

	List<RefreshTokenEntity> findByFamilyId(String familyId);

	/**
	 * 토큰 해시로 계열 식별자만 꺼내는 스칼라 조회다. 엔티티를 만들지 않으므로 영속성 컨텍스트에 아무것도 올리지 않는다.
	 *
	 * 주의. 회전 · 폐기의 첫 조회는 반드시 이 메서드여야 한다. findByTokenHash 로 엔티티를 먼저 로드하면 그 인스턴스가
	 *      영속성 컨텍스트에 managed 로 남고, 뒤이은 findByFamilyIdForUpdate 가 DB 에서 최신 행을 읽어와도 Hibernate 는
	 *      이미 있는 인스턴스를 그대로 돌려주며 필드를 갱신하지 않는다. 그러면 잠금은 실제로 걸렸는데 판정만
	 *      잠금 이전 스냅샷으로 하게 된다.
	 */
	@Query("select r.familyId from RefreshTokenEntity r where r.tokenHash = :tokenHash")
	Optional<String> findFamilyIdByTokenHash(@Param("tokenHash") String tokenHash);

	/**
	 * 계열 전체를 PESSIMISTIC_WRITE 로 한 번에 잠그는 조회다. 회전(rotate) · 명시적 폐기(revoke) · 재사용
	 *      탐지에 의한 계열 폐기가 반드시 이 메서드를 거쳐야, 같은 계열을 동시에 건드리는 트랜잭션들이
	 *      직렬화된다. 모든 호출부가 이 메서드 하나로만 계열을 잠그므로 잠금 순서가 항상 같아 교착이 없다.
	 *
	 * 주의. 잠금 없는 findByFamilyId 로 계열을 스냅샷 뜬 뒤 그 목록으로 폐기하면, 스냅샷과 폐기 사이에
	 *      동시 트랜잭션이 커밋한 새 행(회전으로 막 삽입된 형제 행)이 빠져나간다. 이 메서드로 잠근 뒤
	 *      그 결과로만 판정 · 폐기해야 그런 형제 행도 놓치지 않는다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from RefreshTokenEntity r where r.familyId = :familyId")
	List<RefreshTokenEntity> findByFamilyIdForUpdate(@Param("familyId") String familyId);
}
