package dev.starryeye.custom_authenticate_authentication_event_publisher.provider;

import dev.starryeye.custom_authenticate_authentication_event_publisher.exception.CustomAuthenticationException;
import dev.starryeye.custom_authenticate_authentication_event_publisher.exception.CustomNoMappingAuthenticationException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationProvider;
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

        if(authentication.getName().equals("admin")) {
            /**
             * 인증 객체 이름이 admin 이면, AuthenticationEventPublisher 에 CustomException 를 전달한다.
             * AuthenticationEventPublisher 는 내부적으로 CustomAuthenticationException 이 들어오면
             *      CustomAuthenticationFailureEvent 를 발행하도록 설정되어 있다. (-> SecurityConfig)
             * 따라서, CustomAuthenticationFailureEvent 이벤트가 발행된다.
             */
            eventPublisher.publishAuthenticationFailure(new CustomAuthenticationException("CustomAuthenticationException"), authentication);

            throw new CustomAuthenticationException("CustomAuthenticationException"); // 참고로 이 코드에 의해서도 이벤트가 발행된다.

        }else if(authentication.getName().equals("db")){
            /**
             * 인증 객체 이름이 db 이면, AuthenticationEventPublisher 에 CustomNoMappingAuthenticationException 를 전달한다.
             * AuthenticationEventPublisher 는 내부적으로 CustomNoMappingAuthenticationException 이 들어오면
             *      어떤 이벤트와 매핑될지 설정된게 없으므로 기본 예외 이벤트(CustomDefaultAuthenticationFailureEvent) 발행하도록 설정되어 있다. (-> SecurityConfig)
             * 따라서, CustomDefaultAuthenticationFailureEvent 이벤트가 발행된다.
             */
            eventPublisher.publishAuthenticationFailure(new CustomNoMappingAuthenticationException("CustomNoMappingAuthenticationException"), authentication);

            throw new CustomNoMappingAuthenticationException("CustomNoMappingAuthenticationException"); // 참고로 이 코드에 의해서도 이벤트가 발행된다.
        }
        UserDetails user = User.withUsername("user").password("{noop}1111").roles("USER").build();
        return new UsernamePasswordAuthenticationToken(user, user.getPassword(), user.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return true;
    }
}
