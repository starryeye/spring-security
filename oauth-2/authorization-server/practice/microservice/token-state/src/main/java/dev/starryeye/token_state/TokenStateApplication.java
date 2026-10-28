package dev.starryeye.token_state;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class TokenStateApplication {

	/**
	 * 주의. @EnableScheduling 은 OutboxPublisher 의 @Scheduled 폴러를 돌리기 위한 것이다. 테스트 컨텍스트도
	 *      @SpringBootTest 를 쓰면 이 애노테이션을 물려받아 폴러가 함께 돈다 — 그래서 테스트 yml 이
	 *      my.outbox-poll-interval-ms 를 아주 길게 잡아 사실상 꺼둔다. 발행을 보는 테스트는 publishPending() 을
	 *      직접 부른다.
	 */

	public static void main(String[] args) {
		SpringApplication.run(TokenStateApplication.class, args);
	}
}
