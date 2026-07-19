package dev.starryeye.user_directory.dto;

import java.util.List;

public record UserResponse(String sub, String username, List<String> authorities) {
}
