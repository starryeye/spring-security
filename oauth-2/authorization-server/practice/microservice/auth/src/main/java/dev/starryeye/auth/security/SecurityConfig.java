package dev.starryeye.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

@Configuration
public class SecurityConfig {

	/**
	 * 로그인/세션은 Spring Security 기본 기능을 그대로 쓴다. (SAS 만 제외한다는 원칙)
	 *      RemoteAuthenticationProvider 를 등록해 인증만 user-directory 로 위임하고,
	 *      "/oauth2/authorize" 는 인증을 요구하여 미인증 시 로그인 페이지로 보낸다.
	 */

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http, RemoteAuthenticationProvider provider,
			SessionIdIssuer sessionIdIssuer) throws Exception {
		SavedRequestAwareAuthenticationSuccessHandler successHandler =
				new SavedRequestAwareAuthenticationSuccessHandler();

		http
				.authenticationProvider(provider)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/login", "/error", "/oauth2/logout").permitAll()
						.anyRequest().authenticated()
				)
				.formLogin(form -> form
						.permitAll()
						.successHandler((request, response, authentication) -> {
							// 세션 고정 방어로 세션이 새로 만들어진 뒤이므로 여기가 sid 를 만들 자리다.
							// renew 를 쓴다 — 로그인은 새 OP 세션의 시작이므로, 세션 고정 방어가 속성을
							// 보존해 넘긴 이전 사용자의 sid 가 있어도 여기서 반드시 새로 만든다.
							sessionIdIssuer.renew(request.getSession(true));
							successHandler.onAuthenticationSuccess(request, response, authentication);
						})
				)
				.csrf(csrf -> csrf.ignoringRequestMatchers("/oauth2/authorize")); // GET authorize 는 CSRF 무관, 편의상 제외

		return http.build();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}
}
