package dev.starryeye.custom_multi_auth_ex;

import dev.starryeye.custom_multi_auth_ex.security.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GenerateTokenController {

    private final JwtService jwtService;

    @GetMapping("/gen-token")
    public String generateToken(Authentication authentication) {

        return jwtService.generateToken(authentication.getName());
    }
}
