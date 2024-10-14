package dev.starryeye.custom_authorize_authorization_manager_2.way_2.config;

import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.ComposablePointcut;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableMethodSecurity(prePostEnabled = false) // PreAuthorize, PostAuthorize, PreFilter, PostFilter 비활성화
public class MethodSecurityConfig2 {

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    public Advisor helloServiceAdvisor() {

        /**
         * 포인트컷에 해당하는 메서드를 AuthorityAuthorizationManager 를 이용해서 심사한다.
         * -> HelloService2 의 동적 프록시 객체를 빈으로 등록시키고
         *      해당 빈이 실행될 때 포인트 컷에 해당되면, AuthorityAuthorizationManager 를 이용한 Advisor 가 수행된다.
         */

        return new AuthorizationManagerBeforeMethodInterceptor( // 원본객체의 메서드를 호출하기 전에 권한 심사
                createCompositePointcut(), // 포인트컷
                AuthorityAuthorizationManager.hasRole("USER") // AuthorityAuthorizationManager 로 인증 여부 심사
        );
    }

    public Pointcut createCompositePointcut() {

        /**
         * need..
         * implementation 'org.springframework.boot:spring-boot-starter-aop'
         *
         * AspectJ 표현식 기반 pointcut 말고 커스텀 annotation 을 사용해서도 가능할듯..
         *
         * 한번에 처리도 가능
         * execution(* dev.starryeye.custom_authorize_authorization_manager_2.way_2.service.HelloService2.*)
         */

        // AspectJ 표현식 기반 pointcut 생성
        AspectJExpressionPointcut pointcut1 = new AspectJExpressionPointcut();
        pointcut1.setExpression("execution(* dev.starryeye.custom_authorize_authorization_manager_2.way_2.service.HelloService2.getUser(..))");

        AspectJExpressionPointcut pointcut2 = new AspectJExpressionPointcut();
        pointcut2.setExpression("execution(* dev.starryeye.custom_authorize_authorization_manager_2.way_2.service.HelloService2.getAccount(..))");

        // 두 개의 pointcut 조합이 가능함
        ComposablePointcut compositePointcut = new ComposablePointcut((Pointcut) pointcut1);
        compositePointcut.union((Pointcut) pointcut2);

        return compositePointcut;
    }
}
