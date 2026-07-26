package dev.starryeye.token;

import dev.starryeye.token.client.ClientInfo;
import dev.starryeye.token.client.TokenStateClient;
import dev.starryeye.token.dto.OAuth2ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class RevocationController {

	/**
	 * refresh token 을 폐기한다. (RFC 7009)
	 *
	 * 주의. 폐기 성공 여부와 무관하게 200 을 낸다(RFC 7009 2.2). 존재하지 않는 토큰, 이미 폐기된 토큰,
	 *      다른 client 의 토큰이 전부 같은 응답이라 토큰의 존재 여부를 탐색할 수 없다.
	 *
	 * 주의. refresh token 하나를 폐기하면 그 계열 전체가 죽는다. refresh token 은 하나의 grant 를 대표하므로,
	 *      폐기는 그 grant 를 끝내는 것이다(RFC 7009 2.1). 계열 전체를 죽이는 동작은 token-state 가 구현하며,
	 *      여기서는 위임만 한다.
	 *
	 * 주의. access token 은 폐기하지 않는다. RFC 7009 2 가 access token 폐기를 MAY 로 두므로 표준 위반이 아니며,
	 *      이 서버는 access token 을 짧은 TTL 로 만료시키는 쪽을 택했다. JWT 자가검증의 이점(RS 가 AS 를 호출하지
	 *      않는 것)을 지키기 위함이다.
	 *
	 * 주의. token_type_hint 가 access_token 인지 판정하는 용도 외에는 힌트를 분기에 쓰지 않는다. RFC 7009 도
	 *      힌트가 틀릴 수 있다고 본다. 다만 이 서버는 access token 을 폐기하지 않으므로, refresh token 에
	 *      access_token 힌트가 잘못 붙어 오면 폐기되지 않는다 — 이 설계의 알려진 한계다.
	 */

	private final ClientAuthenticator clientAuthenticator;
	private final TokenStateClient tokenStateClient;

	@PostMapping(value = "/oauth2/revoke", produces = "application/json")
	public ResponseEntity<?> revoke(
			@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
			@RequestParam(value = "token", required = false) String token,
			@RequestParam(value = "token_type_hint", required = false) String tokenTypeHint
	) {
		ClientInfo client;
		try {
			client = clientAuthenticator.authenticate(authorization);
		} catch (ClientAuthenticator.ClientAuthenticationException e) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(new OAuth2ErrorResponse("invalid_client", e.getMessage()));
		}
		if (!StringUtils.hasText(token)) {
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
					.body(new OAuth2ErrorResponse("invalid_request", "token is required"));
		}

		if ("access_token".equals(tokenTypeHint)) {
			log.debug("access token revocation requested. this server expires access tokens instead. clientId={}",
					client.clientId());
			return ResponseEntity.ok().build();
		}

		boolean revoked = tokenStateClient.revoke(token, client.clientId());
		log.info("revocation processed. clientId={} revoked={}", client.clientId(), revoked);
		return ResponseEntity.ok().build();
	}
}
