package dev.starryeye.custom_authenticate_authentication_manager;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

public class CustomAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    /**
     * 커스텀 AuthenticationFilter
     */

    public CustomAuthenticationFilter(HttpSecurity http) {
        super(new AntPathRequestMatcher("/api/login", "GET"));
        setSecurityContextRepository(getSecurityContextRepository(http));
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {

        /**
         * 인증 요청을 가지고 인증 전 버전의 Authentication 을 생성하고 (UsernamePasswordAuthenticationToken)
         * 가지고 있는 AuthenticationManager 에 인증을 위임한다.
         */
        String username = request.getParameter("username"); // query parameter, username 값 접근
        String password = request.getParameter("password"); // query parameter, password 값 접근

        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(username,password);

        return this.getAuthenticationManager().authenticate(token);
    }

    private SecurityContextRepository getSecurityContextRepository(HttpSecurity http) {
        SecurityContextRepository securityContextRepository = http.getSharedObject(SecurityContextRepository.class);
        if (securityContextRepository == null) {
            securityContextRepository = new DelegatingSecurityContextRepository(
                    new RequestAttributeSecurityContextRepository(), new HttpSessionSecurityContextRepository());
        }
        return securityContextRepository;
    }
}
