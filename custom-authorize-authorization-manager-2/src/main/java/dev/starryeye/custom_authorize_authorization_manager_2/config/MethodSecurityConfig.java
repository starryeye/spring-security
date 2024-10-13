package dev.starryeye.custom_authorize_authorization_manager_2.config;

import org.springframework.aop.Advisor;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.security.authorization.method.AuthorizationManagerAfterMethodInterceptor;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableMethodSecurity(prePostEnabled = false) // PreAuthorize, PostAuthorize, PreFilter, PostFilter 비활성화
public class MethodSecurityConfig {

    @Bean
    @Role(value = BeanDefinition.ROLE_INFRASTRUCTURE)
    public Advisor preAuthorizeAdvisor() {
        return AuthorizationManagerBeforeMethodInterceptor.preAuthorize(new CustomPreAuthorizationManager());
    }

    @Bean
    @Role(value = BeanDefinition.ROLE_INFRASTRUCTURE)
    public Advisor postAuthorizeAdvisor() {
        return AuthorizationManagerAfterMethodInterceptor.postAuthorize(new CustomPostAuthorizationManager());
    }
}
