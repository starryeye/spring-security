package dev.starryeye.custom_authenticate_authentication_manager.case_2;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class CustomAuthenticationProvider implements AuthenticationProvider {

    /**
     * 커스텀 AuthenticationProvider
     *
     * AuthenticationManager 로 부터 인증 처리를 위임 받아 인증을 수행하고
     * 완전한 Authentication 객체를 반환한다.
     */

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        /**
         * 인증에 필요한 Authentication 을 AuthenticationManager 로 부터 받아 인증 처리를 수행하고
         * 인증에 성공하면 완전한 Authentication 을 반환한다.
         */
        String loginId = authentication.getName();
        String password = (String) authentication.getCredentials();

        // 무조건 성공 하도록 함.
        return new UsernamePasswordAuthenticationToken(loginId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Override
    public boolean supports(Class<?> authentication) {

        /**
         * AuthenticationManager 가 해당 메서드를 호출하여 인증을 수행할 수 있는 AuthenticationProvider 인지 판별할 수 있다.
         */

        return authentication.isAssignableFrom(UsernamePasswordAuthenticationToken.class);
    }
}
