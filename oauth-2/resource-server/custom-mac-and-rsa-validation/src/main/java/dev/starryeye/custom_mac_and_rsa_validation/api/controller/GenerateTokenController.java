package dev.starryeye.custom_mac_and_rsa_validation.api.controller;

import com.nimbusds.jose.JOSEException;
import dev.starryeye.custom_mac_and_rsa_validation.signature.JwtGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GenerateTokenController {

    private final JwtGenerator jwtGenerator;

    @PostMapping("/token")
    public String generateToken(@AuthenticationPrincipal User user) {
        try {
            return jwtGenerator.generateSignedToken(user);
        } catch (JOSEException e) {
            throw new RuntimeException(e);
        }
    }
}
