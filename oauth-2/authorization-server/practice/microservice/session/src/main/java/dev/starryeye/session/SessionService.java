package dev.starryeye.session;

import dev.starryeye.session.jpa.OidcSessionEntity;
import dev.starryeye.session.jpa.OidcSessionEntityRepository;
import lombok.RequiredArgsConstructor;
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
	 */

	private final OidcSessionEntityRepository repository;

	@Transactional
	public void register(String sid, String sub, String clientId) {
		if (repository.existsBySidAndClientId(sid, clientId)) {
			return;
		}
		repository.save(OidcSessionEntity.builder()
				.sid(sid)
				.sub(sub)
				.clientId(clientId)
				.createdAt(Instant.now())
				.build());
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
