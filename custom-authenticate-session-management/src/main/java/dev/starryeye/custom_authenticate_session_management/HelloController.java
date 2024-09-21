package dev.starryeye.custom_authenticate_session_management;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public Authentication hello(@CurrentSecurityContext SecurityContext securityContext) {
        return securityContext.getAuthentication();
    }

    @GetMapping("invalidSessionUrl")
    public String invalidSessionUrl() {
        return "invalidSessionUrl";
    }

    @GetMapping("expiredUrl")
    public String expiredUrl() {
        return "expiredUrl";
    }
}
