package dev.starryeye.custom_authorize_method_interceptor.config;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthenticatedAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableMethodSecurity(prePostEnabled = false) // PreAuthorize, PostAuthorize, PreFilter, PostFilter 비활성화
public class MethodSecurityConfig {

    @Bean
    public Advisor helloServiceAdvisor() { // Advisor

        // Spring AOP 가 기본적으로 제공하는 Advisor 생성자
        return new DefaultPointcutAdvisor(pointcut(), methodInterceptor());
    }

    private MethodInterceptor methodInterceptor() { // Advice

        // 인증 여부를 심사하는 AuthorizationManager
        AuthorizationManager<MethodInvocation> authorizationManager = new AuthenticatedAuthorizationManager<>();

        // 커스텀 MethodInterceptor (Advice) 생성
        return new CustomMethodInterceptor(authorizationManager);
    }

    private Pointcut pointcut() { // Pointcut

        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression("execution(* dev.starryeye.custom_authorize_method_interceptor.service.HelloService.*(..))");

        return pointcut;
    }
}
