package dev.starryeye.consent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ConsentApplication {

	/**
	 * 사용자가 client 에게 부여한 동의(consent) 기록을 소유하는 서비스이다.
	 *      "누가(sub) 어떤 client 에게 어떤 scope 를 승인했는가" 만 관리하며 화면은 갖지 않는다.
	 *      동의 화면은 로그인 세션과 진행 중 인가 맥락을 가진 auth 가 렌더하고, 이 서비스는 기록의 소유자로만 남는다.
	 *      -> user-directory(사용자 소유), client-registry(client 소유) 와 같은 성격의 내부 데이터 서비스다.
	 */

	public static void main(String[] args) {
		SpringApplication.run(ConsentApplication.class, args);
	}
}
