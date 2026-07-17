package dev.starryeye.client_server.client.exception;

import dev.starryeye.client_server.dto.ErrorResponse;
import lombok.Getter;
import org.springframework.http.HttpStatusCode;

@Getter
public class MyClientErrorException extends RuntimeException {

    private final int statusCode;
    private final ErrorResponse error;

    public MyClientErrorException(HttpStatusCode statusCode, ErrorResponse error) {
        super("Client error (" + statusCode + "): " + error.description());
        this.statusCode = statusCode.value();
        this.error = error;
    }
}
