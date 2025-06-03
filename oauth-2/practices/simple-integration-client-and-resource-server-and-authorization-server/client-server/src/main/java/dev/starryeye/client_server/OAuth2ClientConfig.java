package dev.starryeye.client_server;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;

@Configuration
public class OAuth2ClientConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .requestMatchers("/").permitAll()
                                .anyRequest().authenticated()
                )
                .oauth2Login(oAuth2LoginConfigurer ->
                        oAuth2LoginConfigurer
                                .defaultSuccessUrl("/")
                )
        ;

        return http.build();
    }

    @Bean
    public OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager(
            OAuth2AuthorizedClientRepository authorizedClientRepository,
            ClientRegistrationRepository clientRegistrationRepository
    ) {

        OAuth2AuthorizedClientProvider authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken(
                        refreshTokenGrantBuilder ->
                                // 기본적으로 토큰의 만료시간(expiredAt)에서 60초(기본값) 만큼 빼고나서 현재시간이랑 비교하여 만료 여부를 판단한다.
                                refreshTokenGrantBuilder.clockSkew(Duration.ofSeconds(60L))
                )
                .build();

        DefaultOAuth2AuthorizedClientManager defaultOAuth2AuthorizedClientManager = new DefaultOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientRepository);
        defaultOAuth2AuthorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return defaultOAuth2AuthorizedClientManager;
    }
}
