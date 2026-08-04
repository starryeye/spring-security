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
			// 동시에 들어온 다른 호출이 먼저 같은 (sid, client_id) 행을 만들었다. 원하던 결과는 이미 달성됐다.
		}
	}

	@Transactional
	public LogoutTargets consumeForLogout(String sid) {
		List<OidcSessionEntity> sessions = repository.findBySid(sid);
		if (sessions.isEmpty()) {
			return new LogoutTargets(null, List.of());
		}
		List<String> clientIds = sessions.stream().map(OidcSessionEntity::getClientId).toList();
		String sub = sessions.get(0).getSub();
		repository.deleteBySid(sid);
		return new LogoutTargets(sub, clientIds);
	}
}
