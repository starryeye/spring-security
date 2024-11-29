package dev.starryeye.custom_authenticate_security_context.with_custom_filter;

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
     * AbstractAuthenticationProcessingFilter 는 기본적으로 SecurityContextRepository 를 가지고 있으며
     * RequestAttributeSecurityContextRepository 이다. (Session 이 아닌 요청 단위로 공유함.)
     * -> 이를 Session 으로 돌리기 위해서는 아래 생성자에서 처럼 setSecurityContextRepository 로 명시적으로 설정해줘야한다.
     *      그러면, 인증에 성공 시, HttpSession 에 SecurityContext 를 적재한다.
     *
     * 참고.
     * SecurityConfig 설정에서 SecurityContextPersistenceFilter 설정을 해주면,
     * SecurityContextHolderFilter 와 다르게 응답 시점에 Session 에 SecurityContext 를 저장해버리기 때문에
     * 해당 커스텀 AuthenticationFilter 에서 session 에 저장하는 설정및 코드가 없어도 다음 요청에서 인증이 유지 될 수 있다.
     * 하지만, SecurityContextPersistenceFilter 는 deprecated 이다.
     */

    public CustomAuthenticationFilter(HttpSecurity http) {
        super(new AntPathRequestMatcher("/api/login", "GET"));

        // Custom AuthenticationFilter 는 SecurityContextRepository 를 가지고 있어야한다.
        setSecurityContextRepository(getSecurityContextRepository(http));
    }

    private SecurityContextRepository getSecurityContextRepository(HttpSecurity http) {

        /**
         * Custom AuthenticationFilter 는 추후 사용자의 다음 요청에 인증을 유지 시키기 위해서는
         * 인증 성공 시, SecurityContextRepository 에 Security Context 를 적재 시켜줘야한다.
         *
         * 참고
         * AbstractAuthenticationProcessingFilter 를 상속한다면..
         * AbstractAuthenticationProcessingFilter::doFilter 에서 attemptAuthentication(request, response) 를 호출하면
         * CustomAuthenticationFilter::attemptAuthentication 를 호출하게 되는 것이고..
         * 인증이 성공하면,
         *      AbstractAuthenticationProcessingFilter::doFilter 에서 AbstractAuthenticationProcessingFilter::successfulAuthentication 를 호출하고
         *          여기서 securityContextRepository.saveContext 를 호출한다. (여기서의 securityContextRepository 는 이 메서드에서 생성한 DelegatingSecurityContextRepository 이다.
         * 따라서.. Custom AuthenticationFilter 를 만드는데 AbstractAuthenticationProcessingFilter 를 상속하면
         *      SecurityContextRepository 에 Security Context 를 적재하는 코드는 따로 작성할 필요 없는듯..
         */

        SecurityContextRepository securityContextRepository = http.getSharedObject(SecurityContextRepository.class);

        if (securityContextRepository == null) {
            securityContextRepository = new DelegatingSecurityContextRepository(
                    new HttpSessionSecurityContextRepository(),
                    new RequestAttributeSecurityContextRepository());
        }
        return securityContextRepository;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException {

        String username = request.getParameter("username");
        String password = request.getParameter("password");
        // test 시, http://localhost:8080/api/login?username=user&password=1111 로 테스트하면, 여기로 와서 인증에 성공할 수 있다.
        // todo..
        //      1. 인증 성공 시.. continue 도 안붙고.. localhost:8080/ 주소로 리다이렉트 됨.. why?
        //      2. 인증 한번 성공하고 http://localhost:8080/api/login?username=user&password=1111 를 한번더 호출하면..
        //          SecurityContextHolderFilter 에서 SecurityContext 를 찾았기 때문에 여기를 안타야하는 것 아닌가..

        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(username,password);

        return this.getAuthenticationManager().authenticate(token); // AuthenticationManager 로 인증 처리 위임
    }
}
