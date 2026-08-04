package dev.starryeye.session.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {

	/**
	 * logout token 발송을 사용자 응답 경로에서 떼어낸다.
	 *
	 * 주의. 로그아웃 한 번에 RP 수만큼 POST 가 나간다. 동기로 두면 느린 RP 하나가 사용자의 로그아웃을 붙잡는다.
	 */
}
