package dev.starryeye.custom_client_registration_repository;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class HelloController {

    private final ClientRegistrationRepository clientRegistrationRepository;

    @GetMapping("/")
    public String hello() {

        ClientRegistration clientRegistration = clientRegistrationRepository.findByRegistrationId("my-keycloak");

        return "clientId = " + clientRegistration.getClientId() + ", clientSecret = " + clientRegistration.getClientSecret() + ", redirectUri = " + clientRegistration.getRedirectUri();
    }
}
