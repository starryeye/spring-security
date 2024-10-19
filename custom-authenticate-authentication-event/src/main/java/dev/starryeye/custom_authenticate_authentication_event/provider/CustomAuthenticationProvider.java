package dev.starryeye.custom_authenticate_authentication_event.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationProvider implements AuthenticationProvider {

    private final AuthenticationEventPublisher eventPublisher;
    /**
     * 참고..
     * AuthenticationProvider 는 AuthenticationManager 에 의해 호출되며 실제 인증 처리를 수행한다.
     * UserServiceDetails(UserDetails 를 로드하는 역할) 를 사용한다.
     *
     * 여기서는 커스텀 AuthenticationProvider 를 이용하는 케이스에서.. 직접 이벤트를 발행해본다.
     */

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        // 인증 객체 이름이 user 가 아니면, AuthenticationEventPublisher 를 이용해 실패 이벤트를 발행하도록한다.
        if(!authentication.getName().equals("user")) {

            eventPublisher.publishAuthenticationFailure(
                    new BadCredentialsException("DisabledException"), // 예외를 넘기면 그에 해당하는 실패 이벤트가 매핑되어 실제 발행됨
                    authentication
            );

            throw new BadCredentialsException("BadCredentialsException"); // 참고로 이 코드에 의해서도 결국 이벤트가 발생된다.
        }
        UserDetails user = User.withUsername("user").password("{noop}1111").roles("USER").build();
        return new UsernamePasswordAuthenticationToken(user, user.getPassword(), user.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return true;
    }
}
