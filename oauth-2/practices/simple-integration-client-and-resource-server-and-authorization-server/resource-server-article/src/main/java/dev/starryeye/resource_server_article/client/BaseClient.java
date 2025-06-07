package dev.starryeye.resource_server_article.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.starryeye.resource_server_article.client.exception.MyClientErrorException;
import dev.starryeye.resource_server_article.client.exception.MyServerErrorException;
import dev.starryeye.resource_server_article.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;

@Slf4j
public abstract class BaseClient {

    protected final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    protected BaseClient(
            RestClient.Builder builder,
            String baseUrl,
            ClientHttpRequestInterceptor interceptor
    ) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestInterceptor(interceptor)
                .defaultStatusHandler(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new MyClientErrorException(response.getStatusCode(), parseError(response));
                })
                .defaultStatusHandler(HttpStatusCode::is5xxServerError, (request, response) -> {
                    throw new MyServerErrorException(response.getStatusCode(), parseError(response));
                })
                .build();
    }

    protected <T> T executeSafely(Supplier<T> action) {
        try {
            return action.get();
        } catch (ResourceAccessException e) {
            log.error("네트워크 오류: {}", e.getMessage());
            throw new RuntimeException("네트워크 문제", e);
        } catch (RestClientException e) {
            log.error("RestClient 오류: {}", e.getMessage());
            throw new RuntimeException("API 호출 실패", e);
        }
    }

    private ErrorResponse parseError(ClientHttpResponse response) {
        try (InputStream is = response.getBody()) {
            return objectMapper.readValue(is, ErrorResponse.class);
        } catch (Exception e) {
            try {
                String raw = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                return new ErrorResponse("UNKNOWN", raw);
            } catch (IOException ignored) {
                return new ErrorResponse("UNKNOWN", "본문 파싱 실패");
            }
        }
    }

}
