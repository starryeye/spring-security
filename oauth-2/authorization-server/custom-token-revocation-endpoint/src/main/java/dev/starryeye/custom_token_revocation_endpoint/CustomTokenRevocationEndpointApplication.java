package dev.starryeye.custom_token_revocation_endpoint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomTokenRevocationEndpointApplication {

	public static void main(String[] args) {
		SpringApplication.run(CustomTokenRevocationEndpointApplication.class, args);
	}

	/**
	 * JWT 를 사용하면 stateless 로 authorization server 가 동작할 것으로 착각 할 수 있다.
	 * 그래서, "/revoke" 를 하면.. JWT(access token, refresh token) 에 뭔 작업을 하나.. 라고 생각할 수 있게된다..
	 * 그러면, 기존의 token 은 여전히 활성화 상태가 될 것으로.. 말이 안됨.
	 * -> 폐기 처리한 access token, refresh token 은 authorization server 의 DB 에 persist 하여 블랙리스트로 관리한다.
	 *
	 * 참고.
	 * authorization server 의 DB(or Cache) 에서 관리하는 대표적 정보
	 * 1. SSO 를 위한, authorization server 와 resource owner 의 web browser 간 세션 정보
	 * 2. revoke 된 access token, refresh token 정보
	 * 3. admin 성격의 authorization 설정 정보 (client id, redirect uri 등)
	 * 4. client 에 대한 resource owner 의 consent 정보
	 * 5. authorization code 정보
	 */
}
