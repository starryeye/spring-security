package dev.starryeye.session.event;

import java.time.Instant;

/**
 * OP 세션이 로그아웃됐다는 사실. 소비자가 무엇을 필요로 하는지가 아니라 일어난 일을 서술한다.
 *
 * 주의. clientId 목록을 넣지 않는다. 그것은 "그 세션에 무엇이 붙어 있었나"라는 session 의 내부 상태지
 *      로그아웃이라는 사실이 아니다. 소비자 요구에 맞춰 페이로드를 깎으면 두 번째 소비자가 붙는 순간 깨진다.
 *
 * 주의. sub 는 nullable 이다. 이 이벤트는 로그아웃될 때마다 발행되는데, 등록된 RP 가 하나도 없는
 *      세션(openid 없이 offline_access 만 받은 경로)은 oidc_sessions 행이 없어 소유자를 알 수 없다.
 *      소비자가 폐기에 쓰는 값은 sid 하나이므로 그 경우에도 폐기는 정상 동작한다.
 *
 * 주의. eventId 는 지금 소비자가 쓰지 않는다. 폐기가 조건부 갱신이라 멱등이 공짜이기 때문이다.
 *      나중에 감사 로그처럼 append-only 인 소비자가 붙으면 그쪽은 멱등이 공짜가 아니고, 그때 필요하다.
 */
public record SessionLoggedOutEvent(String eventId, String sid, String sub, Instant occurredAt) {
}
