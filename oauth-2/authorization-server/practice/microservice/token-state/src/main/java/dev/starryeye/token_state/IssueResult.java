package dev.starryeye.token_state;

public record IssueResult(String refreshToken, long expiresAt, String familyId) {
}
