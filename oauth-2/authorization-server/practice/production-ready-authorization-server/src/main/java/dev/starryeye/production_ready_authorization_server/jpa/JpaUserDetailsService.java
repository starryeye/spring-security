package dev.starryeye.production_ready_authorization_server.jpa;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class JpaUserDetailsService implements UserDetailsService {

    /**
     * UserDetailsService 를 JPA 로 직접 구현한다.
     *      form login(UsernamePasswordAuthenticationFilter)과 http basic 모두..
     *      DaoAuthenticationProvider 가 이 서비스로 사용자를 조회한 뒤 password 를 검증한다.
     *
     * password 검증..
     *      "{bcrypt}.." 로 저장되어 있어 기본 DelegatingPasswordEncoder 가 prefix 로 인코더를 찾는다.
     *      client secret 저장과 같은 방식이다. (RegisteredClientController 참고)
     */

    private final UserEntityRepository repository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        UserEntity entity = repository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("user not found: " + username)); // 못 찾으면 null 리턴이 아니라 예외가 인터페이스 규약이다.

        return User.withUsername(entity.getUsername())
                .password(entity.getPassword())
                .authorities(StringUtils.commaDelimitedListToStringArray(entity.getAuthorities()))
                .disabled(!entity.isEnabled())
                .build();
    }
}
