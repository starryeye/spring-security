package dev.starryeye.auth.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	/**
	 * 로그인/세션은 Spring Security 기본 기능을 그대로 쓴다. (SAS 만 제외한다는 원칙)
	 *      RemoteAuthenticationProvider 를 등록해 인증만 user-directory 로 위임하고,
	 *      "/oauth2/authorize" 는 인증을 요구하여 미인증 시 로그인 페이지로 보낸다.
	 */

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http, RemoteAuthenticationProvider provider) throws Exception {
		http
				.authenticationProvider(provider)
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/login", "/error").permitAll()
						.anyRequest().authenticated()
				)
				.formLogin(form -> form.permitAll())
				.csrf(csrf -> csrf.ignoringRequestMatchers("/oauth2/authorize")); // GET authorize 는 CSRF 무관, 편의상 제외

		return http.build();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}
}
