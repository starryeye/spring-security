package dev.starryeye.user_directory.dto;

import java.util.List;

public record AuthenticateResponse(String sub, List<String> authorities) {
}
