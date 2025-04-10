package dev.starryeye.custom_social_login_client_with_form_login.service.security.creator;

import dev.starryeye.custom_social_login_client_with_form_login.model.ProviderUser;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ProviderUserCreatorConfig {

    @Bean
    public ProviderUserCreator<CreateProviderUserRequest, ProviderUser> providerUserCreator() {

        List<ProviderUserCreator<CreateProviderUserRequest, ProviderUser>> creators = List.of(
                new OAuth2GoogleProviderUserCreator(),
                new OAuth2NaverProviderUserCreator(),
                new OAuth2KakaoProviderUserCreator(),
                new OAuth2KeycloakProviderUserCreator()
        );

        return new DelegatingProviderUserCreator(creators);
    }
}
