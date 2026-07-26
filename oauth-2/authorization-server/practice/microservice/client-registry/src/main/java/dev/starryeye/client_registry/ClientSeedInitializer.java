package dev.starryeye.client_registry;

import dev.starryeye.client_registry.jpa.ClientEntity;
import dev.starryeye.client_registry.jpa.ClientEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClientSeedInitializer implements ApplicationRunner {

	/**
	 * client 별로 독립된 존재 검사 후 저장한다. client 가 하나 늘 때마다 앞의 if 를 그대로 복사해 붙이면 된다.
	 *
	 * 주의. `ddl-auto: update` 환경에서는 이미 저장된 행을 이 seed 가 갱신하지 않는다. 기존 행의 컬럼 값이
	 *      바뀐 요구사항과 어긋나면(예: scopes 에 새 scope 가 빠짐) 애플리케이션을 아무리 재기동해도 고쳐지지
	 *      않는다 — DB 를 직접 보정해야 한다.
	 */

	private final ClientEntityRepository repository;

	@Override
	public void run(ApplicationArguments args) {
		PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

		if (!repository.existsById("my-client")) {
			repository.save(ClientEntity.builder()
					.clientId("my-client")
					.clientSecretHash(encoder.encode("secret"))
					.redirectUris("http://127.0.0.1:8080/callback")
					.scopes("openid,profile,email,offline_access")
					.grantTypes("authorization_code,refresh_token")
					.build());
		}

		// resource server 역할. 인가 흐름에 참여하지 않고 introspection 만 호출한다.
		// grant_types 가 비어 있어 토큰 요청은 기존 검사에서 자연히 거절된다.
		if (!repository.existsById("article-api")) {
			repository.save(ClientEntity.builder()
					.clientId("article-api")
					.clientSecretHash(encoder.encode("secret"))
					.redirectUris("")
					.scopes("")
					.grantTypes("")
					.build());
		}
	}
}
