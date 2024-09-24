package dev.starryeye.custom_authenticate_session_registry.controller;

import dev.starryeye.custom_authenticate_session_registry.SessionInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SessionInfoController {

    private final SessionInfoService sessionInfoService;

    @GetMapping("/sessionInfo")
    public String sessionInfo() {
        sessionInfoService.sessionInfo();
        return "sessionInfo";
    }
}
