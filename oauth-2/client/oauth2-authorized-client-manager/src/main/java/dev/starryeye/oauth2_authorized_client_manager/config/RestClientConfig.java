package dev.starryeye.oauth2_authorized_client_manager.config;

import dev.starryeye.oauth2_authorized_client_manager.client.interceptor.OAuth2AuthorizationCodeGrantRequestInterceptor;
import dev.starryeye.oauth2_authorized_client_manager.client.interceptor.OAuth2ClientCredentialsGrantRequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClientForInternalAPI(OAuth2ClientCredentialsGrantRequestInterceptor interceptor) {
        return RestClient.builder()
                .requestInterceptor(interceptor)
                .build();
    }

    @Bean
    public RestClient restClient(OAuth2AuthorizationCodeGrantRequestInterceptor interceptor) {
        return RestClient.builder()
                .requestInterceptor(interceptor)
                .build();
    }
}
