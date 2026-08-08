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
	 * 주의. revokeBySid 는 "이 UPDATE 가 행을 평가하는 순간 ACTIVE 이면서 그 sid 인 행을 폐기한다"만
	 *      보장한다(RefreshTokenService 참고) — 계열 잠금을 쓰지 않으므로, 회전이 동시에 새 ACTIVE 형제
	 *      행을 만들면 이 호출이 그 행을 놓칠 수 있다. 반환값 0은 실패가 아니다(폐기할 것이 없었거나
	 *      이미 폐기됨 = 멱등). 그 "놓친 행" 문제는 이 컨슈머가 풀지 않는다 — 재검증·정합성 스윕은
	 *      이 태스크의 범위 밖이고, outbox(다음 태스크)는 발행 원자성을 다룰 뿐 이 폐기 경쟁을 다루지 않는다.
	 */

	public static final String LOGGED_OUT_TOPIC = "oidc.session.logged-out.v1";

	private final RefreshTokenService refreshTokenService;
	private final ObjectMapper objectMapper;

	@KafkaListener(topics = LOGGED_OUT_TOPIC, groupId = "token-state")
	public void onSessionLoggedOut(String payload) throws Exception {
		SessionLoggedOutEvent event = objectMapper.readValue(payload, SessionLoggedOutEvent.class);
		int revoked = refreshTokenService.revokeBySid(event.sid());
		log.debug("session logged out: sid={} revokedRefreshTokens={} eventId={}",
				event.sid(), revoked, event.eventId());
	}
}
