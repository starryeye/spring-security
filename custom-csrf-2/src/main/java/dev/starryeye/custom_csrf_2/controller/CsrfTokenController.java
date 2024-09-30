package dev.starryeye.custom_csrf_2.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
public class CsrfTokenController {

    @PostMapping("/script/csrfToken")
    public CsrfToken cookieCsrf(CsrfToken csrfToken) {
        return csrfToken;
    }

    @PostMapping("/form/csrfToken")
    public CsrfToken formCsrf(@ModelAttribute FormRequest formRequest, CsrfToken csrfToken) {
        log.info("CsrfToken: {}, username: {}, password: {}",
                csrfToken, formRequest.username(), formRequest.password());
        return csrfToken;
    }
}
