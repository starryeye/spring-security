package dev.starryeye.client_registry;

import dev.starryeye.client_registry.jpa.ClientEntity;
import dev.starryeye.client_registry.jpa.ClientEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClientSeedInitializer implements ApplicationRunner {

	private final ClientEntityRepository repository;

	@Override
	public void run(ApplicationArguments args) {
		if (repository.existsById("my-client")) {
			return;
		}
		repository.save(ClientEntity.builder()
				.clientId("my-client")
				.clientSecretHash(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("secret"))
				.redirectUris("http://127.0.0.1:8080/callback")
				.scopes("openid,profile,email")
				.grantTypes("authorization_code")
				.build());
	}
}
