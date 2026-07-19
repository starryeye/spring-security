package dev.starryeye.token;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PkceValidatorTest {

	// RFC 7636 부록 B 의 예시 벡터
	private static final String VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
	private static final String CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

	@Test
	void matchesKnownVector() {
		assertThat(new PkceValidator().matches(VERIFIER, CHALLENGE)).isTrue();
	}

	@Test
	void rejectsWrongVerifier() {
		assertThat(new PkceValidator().matches("wrong-verifier", CHALLENGE)).isFalse();
	}
}
