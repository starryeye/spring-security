package dev.starryeye.custom_csrf_2.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class CsrfTokenController {

    @PostMapping("/cookieCsrf")
    public CsrfToken cookieCsrf(CsrfToken csrfToken) {
        return csrfToken;
    }

    @PostMapping("/formCsrf")
    public CsrfToken formCsrf(@ModelAttribute FormRequest formRequest) {
        log.info("CsrfToken: {}, username: {}, password: {}",
                formRequest.csrfToken(), formRequest.username(), formRequest.password());
        return formRequest.csrfToken();
    }
}
