package dev.starryeye.custom_authenticate_user_details_service.with_authentication_provider;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.List;

@RequiredArgsConstructor
public class CustomAuthenticationProvider implements AuthenticationProvider {

    private final UserDetailsService userDetailsService;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        /**
         * 실제 인증 수행
         */

        String loginId = authentication.getName();
        String password = (String) authentication.getCredentials();

        // 아이디 검증
        UserDetails userDetails = userDetailsService.loadUserByUsername(loginId);
        if (userDetails == null) {
            throw new UsernameNotFoundException("User not found"); // UserDetailsService 내부에서 해주는 편이 나음, 사용자 정보를 찾지 못하면 UsernameNotFoundException 을 발생시켜야한다.
        }

        // 패스워드 검증 (추후, 학습)

        return new UsernamePasswordAuthenticationToken( // UserDetailsService 로 부터 조회한 UserDetails 를 바탕으로 Authentication 객체 생성
                userDetails.getUsername(),
                userDetails.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_USER")
                ));
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
