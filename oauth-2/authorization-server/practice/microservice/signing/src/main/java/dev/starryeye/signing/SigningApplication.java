package dev.starryeye.signing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SigningApplication {

	/**
	 * JWT 서명을 전담하는 서비스이다.
	 *      개인키(keystore)를 이 서비스만 보유하고, 다른 서비스는 "이 claims 를 서명해달라" 고 요청만 한다.
	 *      -> token 서비스가 털려도 개인키는 노출되지 않는다. (키 격리)
	 */

	public static void main(String[] args) {
		SpringApplication.run(SigningApplication.class, args);
	}
}
