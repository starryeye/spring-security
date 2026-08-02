package dev.starryeye.signing;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.starryeye.signing.dto.SignRequest;
import dev.starryeye.signing.dto.SignResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class SignController {

	/**
	 * "서명 기계" 로서 claims 를 받아 RS256 으로 서명한 JWT 를 돌려준다.
	 *      iss/exp 같은 표준 claim 은 호출자가 채워서 넘긴다. 이 서비스는 정책 판단을 하지 않고 서명 + kid/typ 지정만 한다.
	 *
	 * 주의. typ 은 호출자가 정한다. 같은 키로 access token(at+jwt) · id token(JWT) · logout token(logout+jwt) 이
	 *      서명되므로, 이 헤더가 세 토큰을 구분하는 유일한 표식이다. 어떤 토큰인지는 claim 을 만드는 쪽만 안다.
	 *
	 * 주의. typ 이 없으면 JWT 로 서명한다. at+jwt 를 요구하는 검증기가 그 토큰을 거부하므로, typ 을 보내지 않는
	 *      구버전 호출자가 access token 으로 통하는 JWT 를 만들어낼 수 없다.
	 */

	private static final JOSEObjectType DEFAULT_TYPE = JOSEObjectType.JWT;

	private final JwkKeyProvider keyProvider;

	@PostMapping("/internal/sign")
	public SignResponse sign(@RequestBody SignRequest request) throws Exception {

		JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder();
		request.claims().forEach(claimsBuilder::claim);

		JOSEObjectType type = StringUtils.hasText(request.typ())
				? new JOSEObjectType(request.typ())
				: DEFAULT_TYPE;

		JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
				.keyID(keyProvider.getSigningKey().getKeyID())
				.type(type)
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
