package dev.starryeye.session;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class SessionApplication {

	/**
	 * OP 세션과 RP 의 대응을 소유하고, 로그아웃 시 각 RP 에게 logout token 을 보낸다.
	 *      (OIDC Back-Channel Logout 1.0)
	 *
	 * 주의. @EnableScheduling 은 OutboxPublisher 의 @Scheduled 폴러를 돌리기 위한 것이다. 테스트
	 *      컨텍스트도 @SpringBootTest 를 쓰면 이 애노테이션을 물려받아 폴러가 함께 돈다 — 자동 실행과
	 *      겹치면 안 되는 테스트는 my.outbox-poll-interval-ms 를 아주 길게 잡아 사실상 꺼둔다.
	 */

	public static void main(String[] args) {
		SpringApplication.run(SessionApplication.class, args);
	}
}
