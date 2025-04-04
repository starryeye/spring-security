package dev.starryeye.auth_service.security.ajax_api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.util.StringUtils;

import java.io.IOException;

public class ApiMyAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    private final ObjectMapper objectMapper;

    public ApiMyAuthenticationFilter(HttpSecurity http) {

        // POST /api/login 요청에 대해 수행하는 인증 필터이다.
        super(new AntPathRequestMatcher("/api/login", "POST"));

        // AbstractAuthenticationProcessingFilter::successfulAuthentication 에서 SecurityContext 를 세션에 저장하도록 한다.
        setSecurityContextRepository(getSecurityContextRepository(http));

        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException, ServletException {

        if (!HttpMethod.POST.name().equals(request.getMethod())) {
            throw new IllegalArgumentException("Unsupported HTTP Method: " + request.getMethod());
        }

        if (!MyHttpHeaders.isAjax(request)) {
            throw new IllegalArgumentException("Not an AJAX request.");
        }

        LoginRequest loginRequest = objectMapper.readValue(request.getInputStream(), LoginRequest.class);

        if (!StringUtils.hasText(loginRequest.getUsername()) || !StringUtils.hasText(loginRequest.getPassword())) {
            throw new AuthenticationServiceException("Invalid username or password.");
        }

        ApiMyAuthenticationToken authenticationToken = new ApiMyAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword());

        return this.getAuthenticationManager().authenticate(authenticationToken);
    }

    private SecurityContextRepository getSecurityContextRepository(HttpSecurity http) {
        SecurityContextRepository securityContextRepository = http.getSharedObject(SecurityContextRepository.class);
        if (securityContextRepository == null) {
            securityContextRepository = new DelegatingSecurityContextRepository(
                    new RequestAttributeSecurityContextRepository(),
                    new HttpSessionSecurityContextRepository()
            );
        }
        return securityContextRepository;
    }

    @Getter
    private static class LoginRequest {

        private final String username;
        private final String password;

        @JsonCreator // Json -> object (역직렬화) 때, 보통 no argument constructor 를 사용하는데.. 해당 생성자로 생성하겠다는 어노테이션
        public LoginRequest(
                @JsonProperty("username") String username,
                @JsonProperty("password") String password
        ) {
            this.username = username;
            this.password = password;
        }
    }
}
