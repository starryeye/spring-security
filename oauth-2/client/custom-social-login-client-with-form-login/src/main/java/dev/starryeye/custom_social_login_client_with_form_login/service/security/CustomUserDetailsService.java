package dev.starryeye.custom_social_login_client_with_form_login.service.security;

import dev.starryeye.custom_social_login_client_with_form_login.model.User;
import dev.starryeye.custom_social_login_client_with_form_login.model.creator.CreateProviderUserRequest;
import dev.starryeye.custom_social_login_client_with_form_login.model.creator.ProviderUserCreator;
import dev.starryeye.custom_social_login_client_with_form_login.model.external_provider.ProviderUser;
import dev.starryeye.custom_social_login_client_with_form_login.repository.UserRepository;
import dev.starryeye.custom_social_login_client_with_form_login.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    /**
     * form 인증을 위함.
     * form 인증(로그인) 에서 id/password 를 입력하면..
     *
     * CustomUserDetailsService 에서 DB 와 연동하여 입력한 유저가 존재하는지 검증한다.
     */

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        User user = userRepository.findByUsername(username).orElseThrow(() -> new UsernameNotFoundException(username));

        //todo..

    }
}
