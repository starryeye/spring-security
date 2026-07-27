package dev.starryeye.client_registry;

import dev.starryeye.client_registry.jpa.ClientEntity;
import dev.starryeye.client_registry.jpa.ClientEntityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@SpringBootTest
class ClientSeedInitializerTest {

	@Autowired
	private ClientSeedInitializer initializer;

	@Autowired
	private ClientEntityRepository repository;

	@BeforeEach
	void clean() {
		repository.deleteAll();
	}

	// my-client 가 이미 있어도 article-api 는 따로 삽입돼야 한다.
	// 예전 코드는 my-client 존재 시 즉시 return 해서 article-api 가 영원히 생기지 않았다.
	@Test
	void seedsEachClientIndependently() {
		repository.save(ClientEntity.builder()
				.clientId("my-client")
				.clientSecretHash("{noop}whatever")
				.redirectUris("")
				.scopes("")
				.grantTypes("")
				.clientScopes("")
				.build());

		initializer.run(mock(ApplicationArguments.class));

		assertThat(repository.existsById("article-api")).isTrue();
	}

	@Test
	void seedsBothClientsOnEmptyDatabase() {
		initializer.run(mock(ApplicationArguments.class));

		assertThat(repository.existsById("my-client")).isTrue();
		assertThat(repository.existsById("article-api")).isTrue();
		assertThat(repository.findById("article-api").orElseThrow().getClientScopes()).isEqualTo("introspect");
		assertThat(repository.findById("article-api").orElseThrow().getGrantTypes()).isEqualTo("client_credentials");
	}
}
