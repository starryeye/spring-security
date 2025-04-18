package dev.starryeye.custom_mac_and_rsa_validation.security.filter.username_password.config;

import dev.starryeye.custom_mac_and_rsa_validation.security.filter.username_password.CustomUsernamePasswordAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;

@Configuration
public class UsernamePasswordFilterConfig {

    @Bean
    public CustomUsernamePasswordAuthenticationFilter customUsernamePasswordAuthenticationFilter(AuthenticationManager defaultAuthenticationManager) {
        CustomUsernamePasswordAuthenticationFilter customUsernamePasswordAuthenticationFilter = new CustomUsernamePasswordAuthenticationFilter();
        customUsernamePasswordAuthenticationFilter.setAuthenticationManager(defaultAuthenticationManager);
        return customUsernamePasswordAuthenticationFilter;
    }
}
