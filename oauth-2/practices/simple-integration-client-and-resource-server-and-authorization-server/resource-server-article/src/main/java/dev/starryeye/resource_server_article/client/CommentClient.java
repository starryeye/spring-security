package dev.starryeye.resource_server_article.client;

import dev.starryeye.resource_server_article.client.exception.MyClientErrorException;
import dev.starryeye.resource_server_article.client.exception.MyServerErrorException;
import dev.starryeye.resource_server_article.client.request.GetCommentsRequest;
import dev.starryeye.resource_server_article.dto.Comment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Component
public class CommentClient {

    private final RestClient restClient;

    public CommentClient(RestClient.Builder clientBuilder, ClientHttpRequestInterceptor authorizationInterceptor) {
        this.restClient = clientBuilder
                .baseUrl("http://localhost:8082")
                .requestInterceptor(authorizationInterceptor)
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

    public List<Comment> getCommentsBy(Long contentId) {

        GetCommentsRequest request = new GetCommentsRequest(contentId);

        return executeSafely(() ->
            restClient.post()
                    .uri("/comments")
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Comment>>() {})
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
