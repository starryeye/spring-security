package dev.starryeye.session;

import java.util.List;

public record LogoutTargets(String sub, List<String> clientIds) {
}
