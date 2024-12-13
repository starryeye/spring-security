package dev.starryeye.custom_authenticate_mvc_integration.controller;

import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    private final AuthenticationTrustResolver trustResolver;

    public HelloController() {
        this.trustResolver = new AuthenticationTrustResolverImpl();
    }

    @GetMapping("/")
    public String home() {
        return "Hello World!";
    }

    @GetMapping("/user")
    public String user() {
        return "user";
    }

    @GetMapping("/admin")
    public String admin() {
        return "admin";
    }

    @GetMapping("/db")
    public String db() {
        return "db";
    }

    @GetMapping("/security-context-holder")
    public String securityContextHolder() {
        /**
         * SecurityContextHolder 로 SecurityContext 를 접근할 수 있다.
         * SecurityContext 를 통해 Authentication(인증 객체)에 접근 할 수 있다.
         */
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        /**
         * AuthenticationTrustResolver 를 통해 현재 Authentication 이 Anonymous 인지 확인 할 수 있다.
         */
        if (trustResolver.isAnonymous(authentication)) {
            return "anonymous";
        }

        return "authenticated.. %s".formatted(authentication.getName());
    }

    @GetMapping("/security-context")
    public String securityContext(
            @CurrentSecurityContext SecurityContext securityContext
    ) {
        /**
         * SecurityContext 를 얻을 수 있다.
         *
         * anonymous 의 경우..
         *      principal 이 "anonymousUser" 문자열 객체라서
         *      getName 을 하면.. 그냥 위 문자열이 리턴된다.
         */

        /**
         * 참고..
         *
         * Anonymous 의 경우..
         *      Authentication 는 AnonymousAuthenticationToken..
         *      principal 은 "anonymousUser" (String)
         *      authenticated 는 true
         *
         * 인증을 수행한 경우..
         *      Authentication 은 UsernamePasswordAuthenticationToken..
         *      principal 은 User..
         *      authenticated 는 true 이다.
         */
        return securityContext.getAuthentication().getName();
    }

    @GetMapping("/authentication-principal")
    public User authenticationPrincipal(
            @AuthenticationPrincipal User user
    ) {
        /**
         * @AuthenticationPrincipal 을 통해서 Authentication(인증 객체)의 principal 로 바로 접근할 수 있다.
         */
        return user;
    }

    @GetMapping("/authentication-principal-expression")
    public String authenticationPrincipalExpression(
            @AuthenticationPrincipal(expression = "username") String username
    ) {
        /**
         * @AuthenticationPrincipal 의 expression 요소를 통해 principal(User 객체) 의 필드 값으로 바로 접근이 가능하다.
         * User.username
         */
        return username;
    }

    @GetMapping("/custom-authentication-principal-username")
    public String customAuthenticationPrincipalUsername(
            @CustomAuthenticationPrincipalUsername String username
    ) {

        /**
         * "/authentication-principal-expression" 와 비교하자면..
         * 익명 사용자일 경우에
         *      /authentication-principal-expression 는 에러페이지가 리턴되고..
         *      /custom-authentication-principal-username 는 anonymous 문자열이 리턴된다..
         *
         * /authentication-principal-expression 에서는..
         *      @AuthenticationPrincipal 에서 expression 으로 username 을 사용하도록했는데..
         *      익명사용자의 경우 principal 이 String 객체라서 username 필드가 없기 때문에 에러가 발생한다.
         *
         *
         * 반면..
         * /custom-authentication-principal-username 에서는..
         *      @CustomAuthenticationPrincipalUsername 에서 #this 로 익명사용자일 경우를 필터링 해주고 있어서
         *      에러 발생 없이 정상 동작한다.
         */
        return username;
    }


}
