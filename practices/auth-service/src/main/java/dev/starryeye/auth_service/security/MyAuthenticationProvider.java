package dev.starryeye.auth_service.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MyAuthenticationProvider implements AuthenticationProvider {

    private final UserDetailsService myUserDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {

        String loginRequestId = authentication.getName();
        String loginRequestPassword = authentication.getCredentials().toString();

        // 인증 요청된 Id(loginRequestId) 가 실제 DB 에 존재하는지 확인
        MyUserDetails userDetails = (MyUserDetails) myUserDetailsService.loadUserByUsername(loginRequestId);

        // 인증 요청된 password(loginRequestPassword) 가 실제 DB 에 적재된 Password 와 일치하는지 확인
        if (!passwordEncoder.matches(loginRequestPassword, userDetails.getPassword())) {
            throw new BadCredentialsException("Bad credentials, Invalid password");
        }

        return new UsernamePasswordAuthenticationToken(
                userDetails.getPrincipal(),
                null,
                userDetails.getAuthorities()
        );
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return authentication.isAssignableFrom(UsernamePasswordAuthenticationToken.class);
    }
}
