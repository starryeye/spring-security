package dev.starryeye.custom_authorize_authorization_manager_2.way_1.config;

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

    /**
     * 메서드 기반 권한 심사를 진행하는 어노테이션을 사용할 것인데.. (PreAuthorize, PostAuthorize, PreFilter, PostFilter 등)
     * 커스텀 AuthorizationManager 로 심사를 한다.
     *
     * 참고
     * 메서드 기반 권한 심사를 진행하는 어노테이션은 Spring AOP 로 동작한다.
     * 동적 Proxy 를 생성하는 객체 : InfrastructureAdvisorAutoProxyCreator
     */

    @Bean
    @Role(value = BeanDefinition.ROLE_INFRASTRUCTURE)
    public Advisor preAuthorizeAdvisor() {
        /**
         * Advisor 타입의 빈 등록
         * CustomPreAuthorizationManager 를 사용하는 AuthorizationManagerBeforeMethodInterceptor 를 생성
         *
         * 참고
         * 스프링이 기본으로 사용하는 것은 AuthorizationManagerBeforeMethodInterceptor.preAuthorize() 이다.
         */
        // @PreAuthorize 어노테이션 기능은 CustomPreAuthorizationManager 가 심사한다.
        return AuthorizationManagerBeforeMethodInterceptor.preAuthorize(new CustomPreAuthorizationManager());
    }

    @Bean
    @Role(value = BeanDefinition.ROLE_INFRASTRUCTURE)
    public Advisor postAuthorizeAdvisor() {
        /**
         * Advisor 타입의 빈 등록
         * CustomPostAuthorizationManager 를 사용하는 AuthorizationManagerAfterMethodInterceptor 를 생성
         *
         * 참고
         * 스프링이 기본으로 사용하는 것은 AuthorizationManagerAfterMethodInterceptor.postAuthorize() 이다.
         */
        // @PostAuthorize 어노테이션 기능은 CustomPostAuthorizationManager 가 심사한다.
        return AuthorizationManagerAfterMethodInterceptor.postAuthorize(new CustomPostAuthorizationManager());
    }
}
