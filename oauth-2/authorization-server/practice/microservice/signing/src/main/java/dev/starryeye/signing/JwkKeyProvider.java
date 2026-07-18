package dev.starryeye.signing;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.KeyStore;

@Component
public class JwkKeyProvider {

	/**
	 * keystore(PKCS12) 에서 서명 키를 로드한다.
	 *      RSAKey.load 는 keystore alias 를 kid 로 사용한다. (재기동/다중 인스턴스에서 동일)
	 */

	private final RSAKey signingKey;

	public JwkKeyProvider(
			@Value("${my.signing.key-store-location}") Resource keyStoreLocation,
			@Value("${my.signing.key-store-password}") String keyStorePassword,
			@Value("${my.signing.key-password}") String keyPassword,
			@Value("${my.signing.key-alias}") String keyAlias
	) throws Exception {
		KeyStore keyStore = KeyStore.getInstance("PKCS12");
		try (InputStream is = keyStoreLocation.getInputStream()) {
			keyStore.load(is, keyStorePassword.toCharArray());
		}
		this.signingKey = RSAKey.load(keyStore, keyAlias, keyPassword.toCharArray());
	}

	public RSAKey getSigningKey() {
		return signingKey;
	}

	public JWKSet getPublicJwkSet() {
		return new JWKSet(signingKey.toPublicJWK());
	}
}
