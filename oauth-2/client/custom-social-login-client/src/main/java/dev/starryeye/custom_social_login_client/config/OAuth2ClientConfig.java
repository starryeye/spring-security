package dev.starryeye.custom_social_login_client.config;

import dev.starryeye.custom_social_login_client.service.security.CustomOAuth2UserService;
import dev.starryeye.custom_social_login_client.service.security.CustomOidcUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

@Configuration
@RequiredArgsConstructor
public class OAuth2ClientConfig {

    private final ClientRegistrationRepository clientRegistrationRepository;

    private final CustomOAuth2UserService customOAuth2UserService;
    private final CustomOidcUserService customOidcUserService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .requestMatchers("/images/**", "/css/**", "/js/**").permitAll() // 정적파일접근 허용
                                .requestMatchers("/").permitAll()
                                .requestMatchers("/api/user").hasAnyAuthority("ROLE_SCOPE_email", "ROLE_SCOPE_profile")
                                .requestMatchers("/api/oidc").hasAuthority("ROLE_SCOPE_openid")
                                .anyRequest().authenticated()
                )
                .oauth2Login(oAuth2LoginConfigurer ->
                        oAuth2LoginConfigurer
                                .userInfoEndpoint(userInfoEndpointConfig ->
                                        userInfoEndpointConfig
                                                .userService(customOAuth2UserService)
                                                .oidcUserService(customOidcUserService)
                                )
                )
                .logout( // front channel logout setting
                httpSecurityLogoutConfigurer ->
                        httpSecurityLogoutConfigurer
                                .logoutSuccessHandler(oidcLogoutSuccessHandler())
                                .invalidateHttpSession(true)
                                .clearAuthentication(true)
                                .deleteCookies("JSESSIONID")
                );

        return http.build();
    }

    @Bean
    public GrantedAuthoritiesMapper grantedAuthoritiesMapper() {
        return new CustomGrantedAuthoritiesMapper();
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler() {
        OidcClientInitiatedLogoutSuccessHandler logoutSuccessHandler = new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        logoutSuccessHandler.setPostLogoutRedirectUri("http://localhost:8080/");
        return logoutSuccessHandler;
    }

}
