package dev.starryeye.oauth2_authorized_client_manager_and_filter;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class OAuth2ClientConfig {

    @Bean
    public DefaultOAuth2AuthorizedClientManager defaultOAuth2AuthorizedClientManager(
            OAuth2AuthorizedClientRepository authorizedClientRepository,
            ClientRegistrationRepository clientRegistrationRepository
    ) {

        OAuth2AuthorizedClientProvider authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
                .password() // deprecated..
                .refreshToken()
                .build();

        DefaultOAuth2AuthorizedClientManager defaultOAuth2AuthorizedClientManager = new DefaultOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientRepository);
        defaultOAuth2AuthorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
        defaultOAuth2AuthorizedClientManager.setContextAttributesMapper(this::contextAttributesMapper);

        return defaultOAuth2AuthorizedClientManager;
    }

    private Map<String, Object> contextAttributesMapper(OAuth2AuthorizeRequest oAuth2AuthorizeRequest) {

        HttpServletRequest request = oAuth2AuthorizeRequest.getAttribute(HttpServletRequest.class.getName());
        String username = request.getParameter(OAuth2ParameterNames.USERNAME);
        String password = request.getParameter(OAuth2ParameterNames.PASSWORD);

        Map<String, Object> attributes = new HashMap<>();
        if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
            attributes.put(OAuth2AuthorizationContext.USERNAME_ATTRIBUTE_NAME, username);
            attributes.put(OAuth2AuthorizationContext.PASSWORD_ATTRIBUTE_NAME, password);
        }

        return attributes;
    }
}
