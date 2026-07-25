package dev.starryeye.token.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserProfile(
		String sub,
		String username,
		List<String> authorities,
		String name,
		String nickname,
		String preferredUsername,
		String email,
		boolean emailVerified
) {
}
