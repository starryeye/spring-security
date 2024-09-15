package dev.starryeye.custom_authenticate_authentication_provider;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

public class CustomAuthenticationProvider implements AuthenticationProvider {

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        /**
         * 실제 인증 수행
         */

        String loginId = authentication.getName();
        String password = (String) authentication.getCredentials();

        // 아이디 검증
        // 패스워드 검증

        return new UsernamePasswordAuthenticationToken(loginId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    @Override
    public boolean supports(Class<?> authentication) {

        /**
         * Filter 에서 요청 데이터를 바탕으로 생성한 인증 수행 전 Authentication 객체의 타입과
         * 해당 AuthenticationProvider 에서 인증에 성공할 경우 최종 생성할 Authentication 객체의 타입이 동일한지를 검사한다.
         * -> 이것이 어떤 인증 요청에 대해 해당 AuthenticationProvider 이 처리 가능한지를 알려줄 support method 로 사용된다.
         *
         * 해당 프로젝트에서는 폼인증을 수행할 것이므로 폼 인증을 수행하는 Filter 에서
         * 인증을 위해 생성하는 UsernamePasswordAuthenticationToken 를 support 한다고 한 것이다.
         */

        return authentication.isAssignableFrom(UsernamePasswordAuthenticationToken.class);
    }
}
