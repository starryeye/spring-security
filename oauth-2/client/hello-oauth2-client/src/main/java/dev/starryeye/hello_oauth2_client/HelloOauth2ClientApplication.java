package dev.starryeye.hello_oauth2_client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class HelloOauth2ClientApplication {

	/**
	 * OAuth 2.0 Client 모듈을 구현하려면..
	 * implementation 'org.springframework.boot:spring-boot-starter-oauth2-client' 의존성을 추가한다.
	 *
	 * oauth2-client 의존성을 추가하면, oauth 2.0 기반으로 동작하기 때문에
	 * spring security 의존성을 추가했을때 기본적으로 id / password 를 제공하지 않는다.
	 *
	 * oauth2-client ..
	 * - Authorization server 및 Resource server 와의 통신을 담당하는 Client 의 기능을 필터기반으로 구현하였다.
	 * - Resource owner 를 외부 OAuth 2.0 provider 나 OpenID Connect Provider 계정으로 로그인할 수 있는 기능을 제공한다.
	 * - Authorization code grant, Client credentials grant, Resource owner password credentials grant, Refresh token grant 와 같은 방식을 지원한다.
	 */

	/**
	 * application.yml 에 작성한 설정 값들(client 설정, authorization server 정보)은 OAuth2ClientProperties 의 속성으로 바인딩된다.
	 * OAuth2ClientProperties 의 registration, provider 속성들은 ClientRegistration 클래스에 Map 으로 적재된다.
	 * ClientRegistration 의 값은 OAuth2Client(OAuth2AuthorizedClient) 가 authorization server 와 통신할 때 사용된다.
	 */

	public static void main(String[] args) {
		SpringApplication.run(HelloOauth2ClientApplication.class, args);
	}

}
