package dev.starryeye.user_directory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class UserDirectoryApplication {

	/**
	 * 사용자 신원과 credential 을 소유하는 서비스이다.
	 *      password 비교(bcrypt)를 이 서비스 안에 가둔다. auth 는 평문을 넘겨 검증을 위임할 뿐 password 해시를 보지 않는다.
	 */

	public static void main(String[] args) {
		SpringApplication.run(UserDirectoryApplication.class, args);
	}
}
