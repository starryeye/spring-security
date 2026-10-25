package dev.starryeye.audit;

import java.time.Instant;

/**
 * 감사 레코드 한 줄에 들어갈 값. 소비자가 이벤트에서 뽑아 만든다.
 *
 * 주의. subjectSid 와 reportedSub 는 성격이 다르다. subjectSid 는 발행자가 확실히 아는 값(로그아웃은 sid 로
 *      요청되고 발행자는 그 인자를 그대로 싣는다)이고, reportedSub 는 발행자가 그렇다고 보고한 값일 뿐이다 —
 *      session 은 한 sid 아래 여러 sub 가 섞이는 것을 막지 않고 첫 행의 값을 싣는다(슬라이스 7 설계 5절).
 *      이름이 그 차이를 말하게 했다.
 */
public record AuditRecord(
		String eventId,
		String eventType,
		Instant occurredAt,
		String subjectSid,
		String reportedSub,
		String payload
) {
}
