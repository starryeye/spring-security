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
	 *      탐지에 의한 계열 폐기 · 로그아웃 폐기(revokeBySid)가 반드시 이 메서드를 거쳐야, 같은 계열을 동시에
	 *      건드리는 트랜잭션들이 직렬화된다. 모든 호출부가 이 메서드 하나로만 계열을 잠그므로 잠금 순서가 항상
	 *      같아 교착이 없다.
	 *
	 * 주의. 잠금 없는 findByFamilyId 로 계열을 스냅샷 뜬 뒤 그 목록으로 폐기하면, 스냅샷과 폐기 사이에
	 *      동시 트랜잭션이 커밋한 새 행(회전으로 막 삽입된 형제 행)이 반드시 빠져나간다. h2 에서는 이 메서드로도
	 *      대기 중 삽입된 형제 행이 빠지는 경우가 관찰됐지만(RefreshTokenServiceConcurrentRotateTest 참고), 이는
	 *      h2 가 InnoDB 의 next-key/gap lock 의미론을 재현하지 않는 테스트 DB 의 한계였다. 운영 DB(MySQL
	 *      8/InnoDB)로 RefreshTokenServiceMySqlLockSemanticsTest 를 반복 실행해 확인한 바로는, 잠금을
	 *      기다리던 트랜잭션이 깨어난 뒤의 재조회가 그 형제 행을 항상 포함했다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select r from RefreshTokenEntity r where r.familyId = :familyId")
	List<RefreshTokenEntity> findByFamilyIdForUpdate(@Param("familyId") String familyId);

	/**
	 * 한 OP 세션(sid)에서 주어진 상태의 행을 가진 계열의 식별자만 familyId 순으로 꺼내는 스칼라 조회다. 잠금을 걸지
	 *      않고 엔티티도 만들지 않는다. 로그아웃 폐기(revokeBySid)가 어느 계열을 잠글지 알아내는 데 쓰고, 잠금과 판정은
	 *      그 뒤 계열마다 findByFamilyIdForUpdate 로 한다.
	 *
	 * 주의. 정렬(order by familyId)이 한 로그아웃 안에서 계열을 잠그는 순서다. 여러 계열을 잠그는 호출끼리 같은
	 *      계열들을 언제나 같은 순서로 잠근다.
	 *
	 * 주의. sid 가 null 인 행은 이 조건에 걸리지 않는다 — 실제로 실행되는 비교는 NULL(컬럼) = 'SID-A'
	 *      (파라미터) 형태이고(NULL = NULL 이 아니다), SQL 삼치 논리에서 NULL 을 포함한 비교는 어느 쪽이든
	 *      참이 될 수 없어(UNKNOWN 으로 평가된다) WHERE 절이 그 행을 걸러낸다. sid 가 null 인 행은 sid 가
	 *      없는 code 레코드를 롤링 배포 중에 교환할 때만 생기는데(RefreshTokenEntity.sid 주석), 이 조회가 그
	 *      계열을 돌려주지 않으므로 로그아웃 폐기는 그 행을 폐기하지 못한다. 의도한 동작이 아니라 빈틈이다.
	 */
	@Query("""
			select distinct r.familyId from RefreshTokenEntity r
			 where r.sid = :sid and r.status = :status
			 order by r.familyId
			""")
	List<String> findFamilyIdsBySidAndStatus(@Param("sid") String sid, @Param("status") RefreshTokenStatus status);
}
