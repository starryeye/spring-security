package dev.starryeye.auth_service.security;

import dev.starryeye.auth_service.domain.MyUser;
import dev.starryeye.auth_service.domain.MyUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;

@Component
@RequiredArgsConstructor
public class MyUserDetailsService implements UserDetailsService {

    /**
     * 인증 때, 해당 MyUserDetailsService 를 통해 DB 와 연동하여
     * 인증 요청한 회원이 실제 존재하는지 여부를 확인하도록 한다.
     */

    private final MyUserRepository myUserRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        MyUser myUser = myUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found, username: " + username));

        SimpleGrantedAuthority grantedAuthority = new SimpleGrantedAuthority(myUser.getRoles());
        Collection<SimpleGrantedAuthority> grantedAuthorities = Collections.singletonList(grantedAuthority);

        return new MyUserDetails(
                myUser.getUsername(),
                myUser.getPassword(),
                grantedAuthorities
        );
    }
}
