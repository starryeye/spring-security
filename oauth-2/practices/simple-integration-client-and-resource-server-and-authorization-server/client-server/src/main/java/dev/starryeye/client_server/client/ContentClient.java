package dev.starryeye.client_server.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ContentClient {

    private final RestClient restClient;

    public ContentClient(RestClient.Builder clientBuilder) {
        this.restClient = clientBuilder
                .baseUrl("http://localhost:8081")
                .build();
    }
}
