package dev.starryeye.client_server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Duration;

@Configuration
public class OAuth2ClientConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ClientRegistrationRepository clientRegistrationRepository) throws Exception {

        http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .requestMatchers("/").permitAll()
                                .anyRequest().authenticated()
                )
                .oauth2Login(oAuth2LoginConfigurer ->
                        oAuth2LoginConfigurer
                                .defaultSuccessUrl("/")
                                // authorize 요청에 resource indicator 를 싣기 위한 resolver 교체 (아래 참고)
                                .authorizationEndpoint(authorizationEndpointConfigurer ->
                                        authorizationEndpointConfigurer
                                                .authorizationRequestResolver(resourceIndicatorAuthorizationRequestResolver(clientRegistrationRepository))
                                )
                )
        ;

        return http.build();
    }

    /**
     * authorize 요청에 resource 파라미터(RFC 8707, resource indicator)를 추가한다..
     *      "발급받을 access token 의 대상은 article 서버(http://localhost:8081)" 라고 client 가 요청 시점에 지정하는 것.
     *      authorization server 는 이 값을 검증(invalid_target)하고 access token 의 aud 에 반영하며,
     *      article 서버는 자기 URI 가 aud 에 있어야만 토큰을 수락한다.
     *
     * spring 의 client 설정(yml)에는 resource 파라미터 항목이 없어서..
     *      기본 resolver(DefaultOAuth2AuthorizationRequestResolver)에 커스터마이저로 파라미터를 더해 교체한다.
     */
    private OAuth2AuthorizationRequestResolver resourceIndicatorAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository) {

        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(builder ->
                builder.additionalParameters(additionalParameters -> additionalParameters.put("resource", "http://localhost:8081"))
        );

        return resolver;
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
