package dev.starryeye.signing;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.starryeye.signing.dto.SignRequest;
import dev.starryeye.signing.dto.SignResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SignController {

	/**
	 * "서명 기계" 로서 claims 를 받아 RS256 으로 서명한 JWT 를 돌려준다.
	 *      iss/exp 같은 표준 claim 은 token 서비스가 채워서 넘긴다. 이 서비스는 정책 판단을 하지 않고 서명 + kid 지정만 한다.
	 */

	private final JwkKeyProvider keyProvider;

	@PostMapping("/internal/sign")
	public SignResponse sign(@RequestBody SignRequest request) throws Exception {

		JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder();
		request.claims().forEach(claimsBuilder::claim);

		JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
				.keyID(keyProvider.getSigningKey().getKeyID())
				.build();

		SignedJWT signedJWT = new SignedJWT(header, claimsBuilder.build());
		signedJWT.sign(new RSASSASigner(keyProvider.getSigningKey()));

		return new SignResponse(signedJWT.serialize());
	}

	@GetMapping("/oauth2/jwks")
	public Map<String, Object> jwks() {
		return keyProvider.getPublicJwkSet().toJSONObject();
	}
}
