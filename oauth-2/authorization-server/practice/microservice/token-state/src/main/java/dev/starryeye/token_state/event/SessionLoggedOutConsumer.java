package dev.starryeye.token_state.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.token_state.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SessionLoggedOutConsumer {

	/**
	 * 로그아웃 사실을 받아 그 세션의 refresh token 을 폐기한다.
	 *
	 * 주의. 페이로드에서 쓰는 값은 sid 하나다. sub 는 사실을 서술하려고 실려 있을 뿐 폐기 판정에 쓰지 않는다.
	 *      그래서 sub 가 null 인 이벤트(등록된 RP 가 없던 세션)도 정상 처리된다.
	 *
	 * 주의. 예외를 잡지 않는다. 삼키면 처리하지 못한 로그아웃이 커밋돼 영원히 사라진다. 전파하면
	 *      컨테이너가 재시도하고, 제한을 넘으면 DLT 로 간다(KafkaConsumerConfig 참고).
	 *
	 * 주의. revokeBySid 는 그 세션의 계열마다 rotate 와 같은 계열 잠금(findByFamilyIdForUpdate)을 얻은 뒤 ACTIVE
	 *      행을 폐기하고, 실제로 바뀐 행이 있는 계열마다 REVOKED 사건을 쓴다(RefreshTokenService 참고). 반환값 0은
	 *      실패가 아니다(폐기할 것이 없었거나 이미 폐기됨 = 멱등) — 같은 이벤트가 다시 와도 바뀌는 행이 없어 사건도
	 *      늘지 않는다. 남는 빈틈 중 하나는 revokeBySid 의 첫 조회 뒤에 같은 sid 로 생긴 새 계열이다. 또 하나는 sid 가
	 *      null 인 행이 이 폐기에 걸리지 않는 것이다(RefreshTokenEntityRepository.findFamilyIdsBySidAndStatus 참고).
	 *      이 컨슈머는 어느 쪽도 풀지 않는다 — 놓친 행을 다시 찾아 폐기하는 재검증·정합성 스윕은 어디에도 없다.
	 *
	 * 주의. groupId 를 하드코딩하지 않고 my.consumer-group-id 프로퍼티로 뺀다. 운영값(application.yml)은
	 *      "token-state" 그대로지만, 테스트(application.yml, test)는 "token-state-test" 로 갈라 둔다.
	 *      같은 그룹 id 를 쓰면 스택을 띄워 둔 채(localhost:9092 가 살아 있는 채) 테스트를 돌릴 때 테스트
	 *      JVM 이 운영 컨슈머 그룹에 실제로 조인해 파티션 일부를 가져가 버린다 — 실제 로그아웃 이벤트가
	 *      테스트용 h2 로 소비되고 오프셋만 커밋돼, 운영 쪽 refresh token 은 폐기되지 않는 사고로 이어진다.
	 */

	public static final String LOGGED_OUT_TOPIC = "oidc.session.logged-out.v1";

	private final RefreshTokenService refreshTokenService;
	private final ObjectMapper objectMapper;

	@KafkaListener(topics = LOGGED_OUT_TOPIC, groupId = "${my.consumer-group-id}")
	public void onSessionLoggedOut(String payload) throws Exception {
		SessionLoggedOutEvent event = objectMapper.readValue(payload, SessionLoggedOutEvent.class);
		int revoked = refreshTokenService.revokeBySid(event.sid());
		log.debug("session logged out: sid={} revokedRefreshTokens={} eventId={}",
				event.sid(), revoked, event.eventId());
	}
}
