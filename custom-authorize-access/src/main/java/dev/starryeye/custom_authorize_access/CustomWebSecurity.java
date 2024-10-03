package dev.starryeye.custom_authorize_access;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class CustomWebSecurity {

    public boolean check(Authentication authentication, HttpServletRequest request) {

         return authentication.isAuthenticated(); // 인증 상태이면 true

        // User 권한을 가진 인증이면 true
//        return authentication.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_USER"));
    }
}
