package dev.starryeye.token_state.dto;

public record IssueRequest(String clientId, String sub, String scope, long authTime) {
}
