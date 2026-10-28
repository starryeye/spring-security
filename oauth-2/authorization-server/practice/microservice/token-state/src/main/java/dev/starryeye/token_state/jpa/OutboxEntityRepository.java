package dev.starryeye.token_state.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEntityRepository extends JpaRepository<OutboxEntity, Long> {

	/**
	 * 미발행 행을 오래된 것부터 가져온다. 한 번에 가져오는 수를 제한해 한 주기가 무한정 길어지지 않게 한다.
	 *
	 * 주의. 잠금을 걸지 않는다. 이 스택은 token-state 인스턴스를 하나만 띄우므로 폴러도 하나이기 때문이다.
	 *      인스턴스를 늘리면 폴러끼리 같은 행을 집어 중복 발행이 늘어난다. 소비자가 eventId 로 중복을 걸러야
	 *      피해가 없고, 거르지 않으면 같은 사건이 여러 번 기록된다. 정석 해법은 FOR UPDATE SKIP LOCKED 이며,
	 *      지금 넣으면 도달 불가능한 코드가 되므로 한계로만 남긴다.
	 */
	List<OutboxEntity> findTop100ByPublishedAtIsNullOrderByIdAsc();
}
