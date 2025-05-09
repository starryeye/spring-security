package dev.starryeye.hello_oauth2_authorization_server_2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HelloOauth2AuthorizationServer2Application {

	/**
	 * hello-oauth2-authorization-server 와 비교하여..
	 * hello-oauth2-authorization-server-2 는..
	 * authorization server 를 구축할 때 가장 많이 쓰일 것같은 하나의 방법을 소개함.
	 * (hello-oauth2-authorization-server 의 3번 방법임)
	 */

	public static void main(String[] args) {
		SpringApplication.run(HelloOauth2AuthorizationServer2Application.class, args);
	}

}
