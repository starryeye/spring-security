package dev.starryeye.oauth2_authorized_client_manager.config;

import dev.starryeye.oauth2_authorized_client_manager.client.interceptor.oauth2.AuthorizationCodeGrantRequestInterceptor;
import dev.starryeye.oauth2_authorized_client_manager.client.interceptor.oauth2.ClientCredentialsGrantRequestInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient restClientForInternalAPI(ClientCredentialsGrantRequestInterceptor interceptor) {
        return RestClient.builder()
                .requestInterceptor(interceptor)
                .build();
    }

    @Bean
    public RestClient restClient(AuthorizationCodeGrantRequestInterceptor interceptor) {
        return RestClient.builder()
                .requestInterceptor(interceptor)
                .build();
    }
}
