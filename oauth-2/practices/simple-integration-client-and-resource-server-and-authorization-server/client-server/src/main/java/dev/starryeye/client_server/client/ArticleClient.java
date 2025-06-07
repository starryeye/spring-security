package dev.starryeye.client_server.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.client_server.dto.Article;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

@Slf4j
@Component
public class ArticleClient extends BaseClient {

    public ArticleClient(
            RestClient.Builder builder,
            ClientHttpRequestInterceptor authorizedClientManagerInterceptor
    ) {
        super(builder, "http://localhost:8081", authorizedClientManagerInterceptor);
    }

    public Optional<Article> getArticleBy(Long id) {
        return executeSafely(() ->
                Optional.ofNullable(
                        restClient.get()
                                .uri("/articles/{id}", id)
                                .retrieve()
                                .body(Article.class)
                )
        );
    }
}
