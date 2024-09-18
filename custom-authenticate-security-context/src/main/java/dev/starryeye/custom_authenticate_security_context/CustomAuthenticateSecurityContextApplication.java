package dev.starryeye.custom_authenticate_security_context;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomAuthenticateSecurityContextApplication {

	/**
	 * SecurityContextHolderFilter..
	 * - 수많은 Security Filter 중 거의 첫번째(3 순위) 로 수행된다. (모든 요청에 대해 수행 됨)
	 * - SecurityContextRepository 를 통해 HttpSession 에서 SecurityContext 를 구해와서 SecurityContextHolder 에 저장해준다.
	 * - 다음 필터들은 SecurityContextHolder 를 통해 SecurityContext 및 Authentication 에 접근할 수 있게 된다.
	 *
	 * SecurityContextRepository 인터페이스의 구현체로는 아래가 존재한다.
	 * - HttpSessionSecurityContextRepository : HttpSession 에 SecurityContext 를 연동하여 Application 단위로 공유될 수 있게함.
	 * - RequestAttributeSecurityContextRepository : RequestAttribute 에 SecurityContext 를 연동하여 요청 단위로 공유될 수 있게함.
	 * - NullSecurityContextRepository : SecurityContext 가 필요없는 (JWT, OAuth2) 인증에 사용되고 아무런 처리를 하지 않음
	 * - DelegateSecurityContextRepository : HttpSessionXXX, RequestAttributeXXX 를 동시에 사용가능 하도록 하며, 기본 값이다.
	 *
	 * SecurityContextHolder 는 ThreadLocal 저장소에 SecurityContext 를 적재한다.
	 * SecurityContext 는 Authentication 객체를 적재한다.
	 */

	public static void main(String[] args) {
		SpringApplication.run(CustomAuthenticateSecurityContextApplication.class, args);
	}

}
