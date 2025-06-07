package dev.starryeye.client_server.client;

import dev.starryeye.client_server.client.exception.MyClientErrorException;
import dev.starryeye.client_server.client.exception.MyServerErrorException;
import dev.starryeye.client_server.dto.Article;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Component
public class ArticleClient {

    private final RestClient restClient;

    public ArticleClient(RestClient.Builder clientBuilder, ClientHttpRequestInterceptor authorizedClientManagerInterceptor) {
        this.restClient = clientBuilder
                .baseUrl("http://localhost:8081")
                .requestInterceptor(authorizedClientManagerInterceptor)
                .defaultStatusHandler(
                        HttpStatusCode::is4xxClientError,
                        (request, response) -> {
                            String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                            throw new MyClientErrorException(response.getStatusCode(), body);
                        }
                )
                .defaultStatusHandler(
                        HttpStatusCode::is5xxServerError,
                        (request, response) -> {
                            String body = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                            throw new MyServerErrorException(response.getStatusCode(), body);
                        }
                )
                .build();
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

    public <T> T executeSafely(Supplier<T> action) {
        try {
            return action.get();
        } catch (ResourceAccessException e) { // 커넥션, 타임아웃
            log.error("네트워크 오류: {}", e.getMessage());
            throw new RuntimeException("네트워크 문제", e);
        } catch (RestClientException e) { // 기타 예외
            log.error("RestClient 오류: {}", e.getMessage());
            throw new RuntimeException("API 호출 실패", e);
        }
    }
}
