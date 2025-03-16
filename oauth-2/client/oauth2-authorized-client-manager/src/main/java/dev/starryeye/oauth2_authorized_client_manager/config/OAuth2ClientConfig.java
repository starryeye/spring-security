package dev.starryeye.oauth2_authorized_client_manager.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor
public class OAuth2ClientConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .authorizeHttpRequests(authorizationManagerRequestMatcherRegistry ->
                        authorizationManagerRequestMatcherRegistry
                                .requestMatchers("/").permitAll()
                                .anyRequest().authenticated()
                )
                .oauth2Client(Customizer.withDefaults())
                ;

        return http.build();
    }

    /**
     * OAuth2AuthorizedClientManager..
     *      기본적으로 스프링 빈으로 등록되지 않는 객체이다. (개발자가 필요하면 직접 생성해서 사용, 구현체 DefaultOAuth2AuthorizedClientManager)
     *      OAuth2AuthorizedClient 를 관리하는 용도의 인터페이스이다.
     *      OAuth2AuthorizedClientService, OAuth2AuthorizedClientRepository 를 사용하여 OAuthorizedClient 를 관리한다.
     *      다양한 provider 를 사용하여 OAuth 2.0 권한 부여를 할 수 있다.
     *          Client credential grant : ClientCredentialsOAuth2AuthorizedClientProvider
     *          Authorization code grant : AuthorizationCodeOAuth2AuthorizedClientProvider
     *          Refresh token grant : RefreshTokenOAuth2AuthorizedClientProvider
     *          Resource owner password credentials grant (deprecated) : PasswordOAuth2AuthorizedClientProvider
     *
     * oauth2Login(), oauth2Client(), OAuth2AuthorizedClientManager 비교
     *      oauth2Login()
     *          사용자가 브라우저를 통해 OAuth2 로그인(구글, 카카오, 네이버, GitHub 등)을 할 때 사용한다. 로그인 후, 인증된 사용자 정보를 세션에 저장하고 SecurityContext 를 구성
     *      oauth2Client()
     *          OAuth2 인가 결과(OAuth2AuthorizedClient)를 저장할 수 있도록 한다. 사용자가 client server 에 사용자 리소스 접근 권한 부여
     *          기본적으로 세션 기반 저장소에서만 관리하며, 복잡한 자동 갱신 등의 처리는 지원하지 않는다
     *      OAuth2AuthorizedClientManager
     *          이미 OAuth2 인증이 완료된 client server 의 Access Token 과 Refresh Token 을 관리하며,
     *          Token 이 만료될 경우 Refresh Token 으로 자동 갱신을 수행한다.
     *          특히 API 호출(서버-서버 간 REST API 등) 시 자동으로 토큰 관리가 필요할 때 필수적으로 사용된다.
     *
     * 참고
     * oauth2Login() 과 oauth2Client() 만으로는 OAuth2 인증 결과를 저장하고 사용자를 인증하는 것까지 가능하다.
     *      그러나, REST API 호출처럼 서버가 OAuth2로 보호된 리소스를 호출할 때,
     *      매번 토큰 만료를 체크하거나 새 토큰을 받기 위한 로직을 수동으로 구현해야 하는 번거로움이 발생한다.
     *          매번 API 호출 직전 토큰 만료 여부 확인
     *          만료 시 Refresh Token으로 다시 Access Token 요청
     *          새로운 토큰을 재저장하고 API 호출 반복
     *      이런 복잡한 로직을 OAuth2AuthorizedClientManager 가 자동화 해줌..
     *
     * todo, RestClient 와 연계해서 개발해볼 것.
     */
    @Bean
    public OAuth2AuthorizedClientManager oAuth2AuthorizedClientManager(
            OAuth2AuthorizedClientRepository authorizedClientRepository,
            ClientRegistrationRepository clientRegistrationRepository
    ) {

        OAuth2AuthorizedClientProvider authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()
                .refreshToken()
                .clientCredentials()
                .build();

        DefaultOAuth2AuthorizedClientManager defaultOAuth2AuthorizedClientManager = new DefaultOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientRepository);
        defaultOAuth2AuthorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return defaultOAuth2AuthorizedClientManager;
    }
}
