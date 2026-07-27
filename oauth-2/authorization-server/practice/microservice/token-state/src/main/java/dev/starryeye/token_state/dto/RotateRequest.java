package dev.starryeye.token_state.dto;

/**
 * requestedScope 는 선택이다(RFC 6749 6 의 축소 요청). 없으면(null · 빈 문자열) 축소하지 않는다.
 */
public record RotateRequest(String refreshToken, String clientId, String requestedScope) {
}
