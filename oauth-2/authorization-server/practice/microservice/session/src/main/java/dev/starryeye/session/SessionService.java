package dev.starryeye.session;

import dev.starryeye.session.jpa.OidcSessionEntity;
import dev.starryeye.session.jpa.OidcSessionEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionService {

	/**
	 * OP 세션 레지스트리를 소유한다. 등록은 token 이, 로그아웃 통지는 auth 가 호출한다.
	 *
	 * 주의. consumeForLogout 은 조회와 삭제를 한 번에 한다. 세션은 로그아웃 시점에 끝나므로,
	 *      발송 성공 여부와 무관하게 행을 남기지 않는다. 남기면 다음 로그아웃에서 이미 끝난 세션으로 다시 보낸다.
	 *
	 * 주의. register 의 선검사(existsBySidAndClientId)만으로는 호출자 관점의 멱등이 아니다 — 같은 (sid, client_id) 로
	 *      동시에 두 호출이 들어오면 둘 다 "없음" 을 보고 둘 다 INSERT 를 시도한다. uk_sid_client 유니크 제약이
	 *      중복 행은 막아 데이터 무결성은 지키지만, 진 쪽 트랜잭션은 DataIntegrityViolationException 을 받는다.
	 *      호출자가 보기에도 멱등이려면 그 예외를 성공으로 흡수해야 한다 — 이미 다른 스레드가 원하던 행을
	 *      만들어 놓았다는 뜻이기 때문이다.
	 *
	 * 주의. 다만 DataIntegrityViolationException 을 무조건 흡수하면 안 된다. 이 예외는 유니크 위반뿐 아니라
	 *      컬럼 길이 초과·not null 위반에서도 난다. 무조건 흡수하면 등록이 실제로 실패했는데 호출자에게는
	 *      성공으로 보여, token 쪽 fail-closed(등록 실패 시 토큰 발급 전체 실패)가 조용히 무효화된다 — 그
	 *      RP 는 영원히 로그아웃 통지를 못 받고 아무도 모른다. 그래서 잡은 뒤 existsBySidAndClientId 로
	 *      재확인한다 — 행이 실제로 생겼으면(동시 등록이 이겼으면) 흡수하고, 아니면(다른 제약 위반) 다시 던진다.
	 *
	 * 주의. register 는 메서드 레벨 @Transactional 을 두지 않는다. 감싸면 save 의 flush 실패가 그 바깥
	 *      트랜잭션 자체를 rollback-only 로 표시한다 — 예외를 잡아도 이미 죽은 트랜잭션이라 메서드가
	 *      정상 반환된 뒤 커밋 시점에 UnexpectedRollbackException 이 새로 터진다. existsBySidAndClientId 와
	 *      save 를 각각 레포지토리 자신의(개별) 트랜잭션으로 두면 save 의 실패가 그 트랜잭션 하나로 끝나
	 *      catch 가 실제로 유효하다.
	 */

	private final OidcSessionEntityRepository repository;

	public void register(String sid, String sub, String clientId) {
		if (repository.existsBySidAndClientId(sid, clientId)) {
			return;
		}
		try {
			repository.save(OidcSessionEntity.builder()
					.sid(sid)
					.sub(sub)
					.clientId(clientId)
					.createdAt(Instant.now())
					.build());
		} catch (DataIntegrityViolationException e) {
			// 유니크 위반으로 진 쪽이라면 동시에 들어온 다른 호출이 먼저 같은 (sid, client_id) 행을 만든
			// 것이다 — 재확인해서 행이 실제로 있으면 원하던 결과가 이미 달성된 것이므로 흡수한다.
			// 없으면(길이 초과 등 다른 제약 위반) 등록이 진짜로 실패한 것이므로 다시 던진다.
			if (!repository.existsBySidAndClientId(sid, clientId)) {
				throw e;
			}
		}
	}

	@Transactional
	public LogoutTargets consumeForLogout(String sid) {
		List<OidcSessionEntity> sessions = repository.findBySid(sid);
		List<LogoutTargets.Target> targets = sessions.stream()
				.map(session -> new LogoutTargets.Target(session.getClientId(), session.getSub()))
				.toList();
		repository.deleteBySid(sid);
		return new LogoutTargets(targets);
	}
}
