package dev.starryeye.resource_server_article.client.exception;

import org.springframework.http.HttpStatusCode;

public class MyServerErrorException extends RuntimeException {

    private final int statusCode;
    private final String responseBody;

    public MyServerErrorException(HttpStatusCode statusCode, String responseBody) {
        super("Server error (" + statusCode + "): " + responseBody);
        this.statusCode = statusCode.value();
        this.responseBody = responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }
}

