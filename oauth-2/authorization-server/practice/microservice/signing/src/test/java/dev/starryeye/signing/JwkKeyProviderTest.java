package dev.starryeye.signing;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JwkKeyProviderTest {

	@Autowired
	JwkKeyProvider provider;

	@Test
	void signingKeyHasPrivatePartAndAliasKid() {
		RSAKey key = provider.getSigningKey();
		assertThat(key.isPrivate()).isTrue();
		assertThat(key.getKeyID()).isEqualTo("signing-key-2026");
	}

	@Test
	void publicJwkSetHidesPrivateKey() {
		JWKSet set = provider.getPublicJwkSet();
		assertThat(set.getKeys()).hasSize(1);
		assertThat(set.getKeys().get(0).isPrivate()).isFalse();
	}
}
