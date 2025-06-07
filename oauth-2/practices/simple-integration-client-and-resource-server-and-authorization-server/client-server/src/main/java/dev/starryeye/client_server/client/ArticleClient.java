package dev.starryeye.client_server.client;

import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ArticleClient {

    private final RestClient restClient;

    public ArticleClient(RestClient.Builder clientBuilder, ClientHttpRequestInterceptor authorizedClientManagerInterceptor) {
        this.restClient = clientBuilder
                .baseUrl("http://localhost:8081")
                .requestInterceptor(authorizedClientManagerInterceptor)
                .build();
    }
}
