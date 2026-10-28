package dev.starryeye.audit.config;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	/**
	 * 조회 API 를 이 인가 서버가 발급한 access token 으로만 열어 주는 resource server 설정.
	 *      GET /audit/** 는 scope audit.read 가 있어야 하고 나머지 경로는 모두 거절한다. 오류는 RFC 6750 이다 —
	 *      토큰 없음 401(WWW-Authenticate: Bearer), 무효 401 invalid_token, scope 부족 403 insufficient_scope.
	 *
	 * 주의. 세션을 만들지 않고(STATELESS) CSRF 를 끈다. 이 API 는 요청마다 Bearer 토큰을 싣고 오고 쿠키로 인증하지
	 *      않으므로 CSRF 가 노리는 "브라우저가 자동으로 실어 보내는 자격"이 없다.
	 *
	 * 주의. aud 는 검증하지 않는다. client_credentials 토큰의 aud 는 발급받은 client 자신이라 이 서버를 가리키는 aud 가
	 *      없다(README 알려진 한계 "client_credentials 토큰의 sub == aud").
	 *
	 * 주의. ERROR 디스패치 허용 줄의 근거는 아래 jwtDecoder 의 "jwks 를 받지 못하면" 문단에 있다.
	 */

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.authorizeHttpRequests(authorize -> authorize
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers(HttpMethod.GET, "/audit/**").hasAuthority("SCOPE_audit.read")
						.anyRequest().denyAll())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.csrf(AbstractHttpConfigurer::disable);
		return http.build();
	}

	/**
	 * signing 의 jwks 로 서명을 확인하고 typ · iss · exp 를 검증하는 디코더.
	 *
	 * 주의. typ 을 at+jwt 로 고정한다. signing 은 같은 키로 access token(at+jwt) · id token(JWT) · logout
	 *      token(logout+jwt)을 서명하므로 typ 이 셋을 가르는 유일한 표식이다. spring-security-oauth2-jose 6.4.5 의
	 *      NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder.processor() 는 코드상 DefaultJWTProcessor 를 만들기만 하고 type
	 *      verifier 를 설정하지 않으며, nimbus-jose-jwt 9.37.3 의 DefaultJWTProcessor 의 코드상 기본값은
	 *      DefaultJOSEObjectTypeVerifier.JWT 다 — typ 이 JWT 이거나 없는 토큰만 받는다. 그래서 이 customizer 가 없으면
	 *      보안과 기능이 동시에 거꾸로다. 이 customizer 를 지우면 AuditApiProtectionTest 에서 이 서버의 access
	 *      token(at+jwt)이 401 invalid_token("JOSE header typ (type) at+jwt not allowed")이 되고, typ 만 JWT 로 바꾼
	 *      토큰과 typ 이 없는 토큰은 200 이 된다. customizer 는 빌더가 processor 를 다 만든 뒤 마지막에 적용하므로(코드
	 *      읽기) 이 설정이 기본값을 덮는다.
	 *
	 * 주의. typ 을 type verifier 로 고치는 이유 — 검증기(OAuth2TokenValidator<Jwt>)로는 at+jwt 를 받게 할 수 없다.
	 *      6.4.5 의 spring-security-oauth2-jose 에는 JwtTypeValidator 가 없다. 직접 만든 검증기도 jwt.getHeaders() 로
	 *      typ 을 읽을 수는 있지만, NimbusJwtDecoder.decode 는 createJwt 안에서 JWTProcessor.process 를 먼저 돌리고 그
	 *      뒤에 validateJwt 로 검증기를 돌리므로, 기본 type verifier 가 process 안에서 at+jwt 를 이미 거부한 뒤다(코드
	 *      읽기: NimbusJwtDecoder.decode · createJwt). customizer 를 지웠을 때 at+jwt 를 거부하는 문구("JOSE header typ
	 *      (type) at+jwt not allowed")도 Nimbus DefaultJOSEObjectTypeVerifier 의 것이다(코드 읽기).
	 *
	 * 주의. iss · exp 는 JwtValidators.createDefaultWithIssuer 가 본다. 그 안의 JwtTimestampValidator 는 코드상 기본
	 *      시계 오차 허용이 60초라, 만료 뒤 60초까지의 토큰은 아직 받는다.
	 *
	 * 주의. jwks 는 캐시된다. .cache(...) 를 주지 않으면 빌더가 코드상 failover 없는 new RemoteJWKSet(url, retriever)
	 *      를 만들고, 그 캐시(nimbus-jose-jwt 9.37.3 의 DefaultJWKSetCache)의 코드상 기본값은 수명 15분 · 갱신 시점 5분이다.
	 *      RemoteJWKSet.get 은 코드상 갱신 시점이 지나면 요청마다 다시 받으려 하고, 받기에 실패해도 수명 안의 캐시가
	 *      있으면 그 키로 계속하며, 수명이 지나 캐시가 비면 예외를 던진다. 그래서 signing 이 내려간 동안 캐시로 버티는
	 *      창은 마지막으로 받은 뒤 최대 15분이고, 5분이 지난 뒤로는 요청마다 실패하는 jwks 호출이 하나씩 나간다 — 이
	 *      시간들은 코드 읽기에서 나온 값이고 측정값이 아니다. 테스트(AuditApiJwksCacheTest)가 보이는 것은 "키를 받은
	 *      직후 jwks 가 내려가도 200"뿐이다.
	 *
	 * 주의. jwks 를 받지 못하면(캐시도 없으면) 401 이 아니라 500 이다. 키를 확보하지 못한 것은 우리 쪽 장애이지
	 *      토큰의 잘못이 아니다 — 401 invalid_token 으로 답하면 호출자는 멀쩡한 토큰을 버린다(token 의
	 *      AccessTokenVerifier 와 같은 원칙). NimbusJwtDecoder 는 코드상 RemoteKeySourceException 을 BadJwtException 이
	 *      아닌 JwtException 으로 바꾸고, JwtAuthenticationProvider 는 코드상 그것을 AuthenticationServiceException 으로 올리며,
	 *      spring-security-web 6.4.5 의 AuthenticationEntryPointFailureHandler 는 코드상 rethrowAuthenticationServiceException
	 *      기본값이 true 라 그 예외를 401 로 바꾸지 않고 다시 던진다. 예외는 컨테이너까지 올라가 /error 로 ERROR
	 *      디스패치되고, 그 디스패치도 보안 필터를 다시 지난다.
	 *
	 * 주의. 그래서 securityFilterChain 의 dispatcherTypeMatchers(DispatcherType.ERROR).permitAll() 이 필요하다. 원래
	 *      요청은 인증에 실패해 인증 정보가 없으므로, 이 줄이 없으면 /error 디스패치가 anyRequest().denyAll() 에 걸려
	 *      응답이 500 이 아니라 401(WWW-Authenticate: Bearer)로 바뀐다 — 이 줄을 지우면 AuditApiJwksUnavailableTest 가
	 *      그 401 을 받아 실패한다. 이 줄은 디스패처 종류로만 고르므로 밖에서 /error 를 직접 부르는 요청(REQUEST
	 *      디스패치)은 열리지 않고 denyAll 에 걸린다.
	 */
	@Bean
	JwtDecoder jwtDecoder(@Value("${my.jwk-set-uri}") String jwkSetUri, @Value("${my.issuer}") String issuer) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
				.jwtProcessorCustomizer(processor -> processor.setJWSTypeVerifier(
						new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType("at+jwt"))))
				.build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
		return decoder;
	}
}
