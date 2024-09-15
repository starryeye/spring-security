package dev.starryeye.custom_authenticate_user_details_service;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

public class CustomUserDetailsService implements UserDetailsService {
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        /**
         * DB 나 memory 로 부터 사용자 정보를 조회하고 UserDetails 타입으로 생성 후 반환한다. (by username)
         *
         * 존재하지 않으면 UsernameNotFoundException 을 발생시킨다.
         */
        return User.withUsername("user").password("{noop}1111").roles("USER").build();
    }
}
