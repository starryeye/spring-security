package dev.starryeye.token;

import dev.starryeye.token.dto.OAuth2ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OAuth2ExceptionHandler {

	/**
	 * 미처리 예외를 OAuth2 에러 포맷(server_error)으로 정규화한다.
	 *      토큰 엔드포인트 계약은 "{access_token} 또는 OAuth2 에러" 이므로 Spring 기본 500 바디(timestamp/path 등)가 새어나가면 안 된다.
	 *      (downstream 서비스 장애 등 예상 못한 예외가 여기로 온다)
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<OAuth2ErrorResponse> handleUnexpected(Exception e) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(new OAuth2ErrorResponse("server_error", "unexpected error"));
	}
}
