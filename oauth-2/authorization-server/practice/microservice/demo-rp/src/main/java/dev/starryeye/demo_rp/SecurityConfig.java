package dev.starryeye.demo_rp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	/**
	 * oauth2Login 으로 로그인하고, 두 방향의 로그아웃을 모두 켠다.
	 *      logout()      — RP 가 시작하는 로그아웃. OidcClientInitiatedLogoutSuccessHandler 가
	 *                      discovery 의 end_session_endpoint 로 사용자를 보낸다.
	 *      oidcLogout()  — OP 가 보내는 back-channel logout 을 받는다.
	 *                      수신 경로는 /logout/connect/back-channel/{registrationId} 다.
	 *
	 * 주의. back-channel 수신 경로는 인증을 요구하면 안 된다. OP 가 사용자 세션 없이 서버 대 서버로 POST 한다.
	 *
	 * 주의. demo-rp 는 client-secret 을 쓰는 confidential client 라 Spring Security 가 기본으로는
	 *      PKCE 파라미터를 붙이지 않는다(자동 PKCE 는 client-authentication-method=none 인 public client 에만 적용된다).
	 *      이 AS 의 AuthorizeController 는 client 종류와 무관하게 PKCE(S256) 를 항상 강제하므로,
	 *      authorizationRequestResolver 에 OAuth2AuthorizationRequestCustomizers.withPkce() 를 명시적으로 걸어야 한다.
	 */

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http,
			ClientRegistrationRepository clientRegistrationRepository) throws Exception {
		OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler =
				new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
		logoutSuccessHandler.setPostLogoutRedirectUri("http://localhost:8095/");

		DefaultOAuth2AuthorizationRequestResolver authorizationRequestResolver =
				new DefaultOAuth2AuthorizationRequestResolver(
						clientRegistrationRepository,
						OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
		authorizationRequestResolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

		http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/logout/connect/back-channel/**").permitAll()
						.anyRequest().authenticated()
				)
				.oauth2Login(oauth2 -> oauth2
						.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(authorizationRequestResolver))
				)
				.logout(logout -> logout.logoutSuccessHandler(logoutSuccessHandler))
				.oidcLogout(oidc -> oidc.backChannel(Customizer.withDefaults()))
				.csrf(csrf -> csrf.ignoringRequestMatchers("/logout/connect/back-channel/**"));

		return http.build();
	}
}
