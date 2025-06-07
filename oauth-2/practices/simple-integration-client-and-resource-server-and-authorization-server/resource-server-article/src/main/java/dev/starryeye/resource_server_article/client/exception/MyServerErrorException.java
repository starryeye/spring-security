package dev.starryeye.resource_server_article.client.exception;

import dev.starryeye.resource_server_article.dto.ErrorResponse;
import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class MyServerErrorException extends RuntimeException {

    private final int statusCode;
    private final ErrorResponse error;

    public MyServerErrorException(HttpStatusCode statusCode, ErrorResponse error) {
        super("Server error (" + statusCode + "): " + error.description());
        this.statusCode = statusCode.value();
        this.error = error;
    }
}

