package dev.starryeye.token_state;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenGeneratorTest {

	private final TokenGenerator generator = new TokenGenerator();

	@Test
	void generatesUrlSafeTokenWithoutPadding() {
		String token = generator.generate();

		assertThat(token).matches("[A-Za-z0-9_-]+"); // base64url, padding 없음
		assertThat(token).hasSizeGreaterThanOrEqualTo(43); // 256비트 = 43자
	}

	@Test
	void generatesDistinctTokens() {
		Set<String> tokens = new HashSet<>();
		for (int i = 0; i < 1000; i++) {
			tokens.add(generator.generate());
		}
		assertThat(tokens).hasSize(1000);
	}

	// 해시로 행을 찾아야 하므로 같은 입력은 반드시 같은 출력이어야 한다 (salt 를 쓸 수 없는 이유)
	@Test
	void hashIsDeterministic() {
		assertThat(generator.hash("abc")).isEqualTo(generator.hash("abc"));
	}

	// SHA-256 의 알려진 벡터. 직접 구현한 해시가 실제로 SHA-256 인지 못 박는다.
	@Test
	void hashMatchesKnownSha256Vector() {
		assertThat(generator.hash("abc"))
				.isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
	}

	@Test
	void differentInputsProduceDifferentHashes() {
		assertThat(generator.hash("abc")).isNotEqualTo(generator.hash("abd"));
	}

	// null 은 SHA-256 이 없다는 뜻이 아니다. 넓은 catch 가 NPE 를 삼켜 원인을 오도하지 않는지 고정한다.
	@Test
	void nullInputSurfacesAsNullPointerNotAlgorithmFailure() {
		assertThatThrownBy(() -> generator.hash(null))
				.isInstanceOf(NullPointerException.class);
	}
}
