package dev.starryeye.audit;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.function.Predicate;

final class IdempotentInsert {

	/**
	 * eventId 로 한 줄을 멱등하게 넣는다. 같은 eventId 가 다시 오면 아무것도 하지 않는다. 감사의 기록기들
	 *      (AuditRecorder, RefreshTokenEventRecorder)이 같은 알고리즘을 쓰도록 한 곳에 둔 것이다. 행을 만드는 일
	 *      (save)과 있는지 보는 일(exists)은 기록기가 넘긴다 — 두 기록기는 테이블이 다를 뿐 이 알고리즘이 같다.
	 *
	 * 주의. 선조회(exists)만으로는 멱등이 아니다. 같은 eventId 가 거의 동시에 두 번 도착하면 둘 다 "없음"을 보고
	 *      둘 다 INSERT 를 시도하고, 진 쪽이 eventId 의 유니크 제약 위반을 맞는다. 그 위반을 흡수해야 호출자(소비자)
	 *      관점에서도 멱등이다 — 중복은 예외가 아니라 false 여야 한다. 흡수하지 않으면 멀쩡한 중복 전달이 실패로
	 *      취급돼 불필요한 에러 로그와 재시도를 낸다. 다만 DLT 까지는 가지 않는다 — 이 위반은 상대 전달이 그 행을
	 *      커밋한 뒤에 나므로, 소비자가 재시도하면 선조회가 그 행을 보고 false 로 끝난다. 재시도를 소진해도 안 풀리는
	 *      것은 아래의 다시 던지는 갈래(다른 제약 위반)다.
	 *
	 * 주의. 다만 DataIntegrityViolationException 을 무조건 흡수하면 안 된다. 이 예외는 유니크 위반뿐 아니라
	 *      not null 위반에서도 난다(필수 값이 빠진 기형 이벤트). 무조건 흡수하면 기록에 실패한 이벤트가 성공으로
	 *      넘어가 감사가 그 기록을 조용히 잃는다. 그래서 잡은 뒤 exists 로 재확인한다 — 행이 실제로 있으면(다른
	 *      전달이 먼저 넣었으면) 흡수하고, 없으면 다시 던진다.
	 *
	 * 주의. 이 메서드에는 @Transactional 을 두지 않고, 호출하는 기록기에도 두지 않는다. 감싸면 IDENTITY 채번이 save
	 *      시점에 즉시 flush 하다 실패하면서 그 바깥 트랜잭션을 rollback-only 로 오염시켜, 예외를 잡아도 커밋 시점에
	 *      UnexpectedRollbackException 이 새로 터진다. exists 와 save 를 각각 레포지토리 자신의 트랜잭션으로 두어야
	 *      catch 가 실제로 유효하다. session 의 SessionService.register 와 같은 이유·같은 모양이다.
	 *
	 * 주의. eventId 만 본다. 같은 eventId 인데 내용이 다른 편지가 와도 같은 이벤트로 취급해 흡수한다.
	 *
	 * @return 새로 넣었으면 true, 이미 있어 흡수했으면 false
	 */
	static boolean insertOnce(String eventId, Predicate<String> exists, Runnable save) {
		if (exists.test(eventId)) {
			return false;
		}
		try {
			save.run();
			return true;
		} catch (DataIntegrityViolationException e) {
			// 유니크 위반으로 진 쪽이면 다른 전달이 같은 eventId 행을 먼저 만든 것이다 — 원하던 결과가 이미 있으므로 흡수한다.
			// 없으면(not null 등 다른 제약 위반) 기록이 진짜로 실패한 것이므로 다시 던진다.
			if (!exists.test(eventId)) {
				throw e;
			}
			return false;
		}
	}

	private IdempotentInsert() {
	}
}
