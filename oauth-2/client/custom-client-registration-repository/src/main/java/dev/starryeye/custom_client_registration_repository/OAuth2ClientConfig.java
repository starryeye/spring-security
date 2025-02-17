package dev.starryeye.custom_client_registration_repository;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

import java.util.List;

@Configuration
public class OAuth2ClientConfig {

    /**
     * ClientRegistrationRepository 는..
     *      ClientRegistration 의 저장소 역할을 한다.
     *      직접 개발자가 빈 등록하지 않으면 auto configuration 으로 빈 등록된다.
     *      ClientRegistrationRepository::findByRegistrationId api 를 이용하여
     *          런타임 시점에 ClientRegistration 을 얻어 필요한 정보를 얻을 수 있다. (HelloController 참조)
     */

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        return new InMemoryClientRegistrationRepository(keycloakClientRegistration());
    }

    private ClientRegistration keycloakClientRegistration() {

        /**
         * ClientRegistrations::fromIssuerLocation api 를 사용하면,
         * http://localhost:8090/realms/custom-realm/.well-known/openid-configuration 에 접근하여
         * 필요한 정보를 땡겨온다.
         *
         * 참고
         * ClientRegistration::withRegistrationId api 를 이용하여 전체를 설정해도 된다.
         */
        return ClientRegistrations.fromIssuerLocation("http://localhost:8090/realms/custom-realm")
                .registrationId("my-keycloak")
                .clientId("custom-client-app")
                .clientSecret("kAjcXgrLnfPZNRcqjdynH5uO0ACiqK6y")
                .redirectUri("http://localhost:8080/login/oauth2/code/my-keycloak")
                .scope(List.of("openid", "profile", "email"))
                .build();
    }
}
