package dev.starryeye.token_state.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

/**
 * session 이 발행하는 로그아웃 사실의 소비자 쪽 표현이다. 발행자와 같은 필드를 각각 둔다 —
 *      모듈 간 공유 라이브러리를 만들지 않는 것이 이 저장소의 방식이다(cross-service record 는 슬라이스 1부터 그렇다).
 *
 * 주의. 모르는 필드는 무시한다(@JsonIgnoreProperties). 발행자가 필드를 더해도 소비자가 터지지 않게 하려는 것인데,
 *      대가가 있다 — 슬라이스 3에서 검증 필드를 추가했을 때 구버전이 그것을 조용히 무시해 검증이 통째로 뚫렸다.
 *      그래서 보안 판단은 새 필드로 추가하지 않고 토픽 버전을 올린다. 토픽 이름의 .v1 이 그 준비다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SessionLoggedOutEvent(String eventId, String sid, String sub, Instant occurredAt) {
}
