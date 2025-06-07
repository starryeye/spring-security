package dev.starryeye.resource_server_article.client;

import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CommentClient {

    private final RestClient restClient;

    public CommentClient(RestClient.Builder clientBuilder, ClientHttpRequestInterceptor authorizationInterceptor) {
        this.restClient = clientBuilder
                .baseUrl("http://localhost:8082")
                .requestInterceptor(authorizationInterceptor)
                .build();
    }
}
