package dev.starryeye.resource_server_article.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.resource_server_article.client.request.GetCommentsRequest;
import dev.starryeye.resource_server_article.dto.Comment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Slf4j
@Component
public class CommentClient extends BaseClient{

    public CommentClient(
            RestClient.Builder clientBuilder,
            ClientHttpRequestInterceptor authorizationInterceptor
    ) {
       super(clientBuilder, "http://localhost:8082", authorizationInterceptor);
    }

    public List<Comment> getCommentsBy(Long contentId) {

        GetCommentsRequest request = new GetCommentsRequest(contentId);

        return executeSafely(() ->
                restClient.post()
                        .uri("/comments")
                        .body(request)
                        .retrieve()
                        .body(new ParameterizedTypeReference<List<Comment>>() {
                        })
        );
    }
}
