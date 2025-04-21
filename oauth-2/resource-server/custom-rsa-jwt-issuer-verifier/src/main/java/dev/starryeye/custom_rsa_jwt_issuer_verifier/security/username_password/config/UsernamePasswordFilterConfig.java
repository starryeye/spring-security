package dev.starryeye.custom_rsa_jwt_issuer_verifier.security.username_password.config;

import dev.starryeye.custom_rsa_jwt_issuer_verifier.security.username_password.CustomUsernamePasswordAuthenticationFilter;
import dev.starryeye.custom_rsa_jwt_issuer_verifier.security.username_password.success_handler.JwtIssuerOnAuthenticationSuccess;
import dev.starryeye.custom_rsa_jwt_issuer_verifier.signature.JwtGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
public class UsernamePasswordFilterConfig {

    @Bean
    public CustomUsernamePasswordAuthenticationFilter customUsernamePasswordAuthenticationFilter(
            AuthenticationManager defaultAuthenticationManager,
            AuthenticationSuccessHandler jwtIssuerOnAuthenticationSuccess
    ) {
        AntPathRequestMatcher requestMatcher = new AntPathRequestMatcher("/token", "POST");
        CustomUsernamePasswordAuthenticationFilter customUsernamePasswordAuthenticationFilter = new CustomUsernamePasswordAuthenticationFilter();
        customUsernamePasswordAuthenticationFilter.setAuthenticationManager(defaultAuthenticationManager);
        customUsernamePasswordAuthenticationFilter.setRequiresAuthenticationRequestMatcher(requestMatcher);
        customUsernamePasswordAuthenticationFilter.setAuthenticationSuccessHandler(jwtIssuerOnAuthenticationSuccess);
        return customUsernamePasswordAuthenticationFilter;
    }

    @Bean
    public AuthenticationSuccessHandler jwtIssuerOnAuthenticationSuccess(JwtGenerator jwtGenerator) {
        return new JwtIssuerOnAuthenticationSuccess(jwtGenerator);
    }
}
