package dev.starryeye.custom_authenticate_login_controller.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class LoginController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    @PostMapping("/login")
    public Authentication login(@RequestBody LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response) {

        // 인증 요청 데이터를 바탕으로 인증 처리 전 토큰 생성
        UsernamePasswordAuthenticationToken unauthenticated = UsernamePasswordAuthenticationToken.unauthenticated(loginRequest.username(), loginRequest.password());

        // 인증 처리 수행
        Authentication authentication = this.authenticationManager.authenticate(unauthenticated);

        // SecurityContext 를 생성하고 요청 단위로 공유되도록 한다.
        SecurityContext securityContext = SecurityContextHolder.getContextHolderStrategy().createEmptyContext(); // 비어있는 SecurityContext 생성
        securityContext.setAuthentication(authentication); // 인증 결과를 SecurityContext 에 저장한다.
        SecurityContextHolder.getContextHolderStrategy().setContext(securityContext); // SecurityContext 를 SecurityContextHolder (ThreadLocal) 에 저장한다.

        // SecurityContext 를 세션에 저장하여 인증 상태를 영속화한다.
        this.securityContextRepository.saveContext(securityContext, request, response);

        return authentication;
    }
}
