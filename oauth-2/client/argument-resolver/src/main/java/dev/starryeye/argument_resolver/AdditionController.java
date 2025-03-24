package dev.starryeye.argument_resolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizationSuccessHandler;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
public class AdditionController {

    /**
     * HelloController 에서 알아본 지식을 조금 응용해보는 Controller 이다.
     */

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> userService;
    private final OAuth2AuthorizationSuccessHandler authorizationSuccessHandler;
    private final SecurityContextRepository securityContextRepository;

    public AdditionController(OAuth2AuthorizedClientRepository authorizedClientRepository) {
        this.userService = new DefaultOAuth2UserService();
        this.authorizationSuccessHandler = (authorizedClient, principal, attributes) -> authorizedClientRepository
                .saveAuthorizedClient(
                        authorizedClient,
                        principal,
                        (HttpServletRequest) attributes.get(HttpServletRequest.class.getName()),
                        (HttpServletResponse) attributes.get(HttpServletResponse.class.getName())
                );
        this.securityContextRepository = new HttpSessionSecurityContextRepository(); // 세션 기반
    }

    @GetMapping("/is-authenticated")
    public Authentication isAuthenticated() {
        return SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
    }

    @GetMapping("/authorized-client-password")
    public OAuth2AuthorizedClient authorizedClientPassword(@RegisteredOAuth2AuthorizedClient("my-keycloak-password-credentials") OAuth2AuthorizedClient authorizedClient) {

        /**
         * /authorized-client?username=user&password=1111 로 호출해야한다.
         *      OAuth2ClientConfig 에서 스프링 빈으로 등록한 DefaultOAuth2AuthorizedClientManager 를 사용해야 password credential 로 동작 가능.
         *          커스텀 contextAttributesMapper 이 동작 되어야 username, password 를 얻을 수 있게된다.
         *
         * 참고
         * 1. DefaultOAuth2AuthorizedClientManager 에 커스텀 contextAttributesMapper 를 해주지 않고 그냥 스프링이 자동으로 만들어주는 것을 사용하게 되면..
         *      OAuth2AuthorizedClientArgumentResolver 에서는 인가 처리를 하지 못해서 oauth2Login 에 의한 redirect 되어 의도에서 벗어나게 된다.
         * 2. my-keycloak 으로 동작시켜도 DefaultOAuth2AuthorizedClientManager 는 AuthorizationCodeOAuth2AuthorizedClientProvider 를 가지지만..
         *      authorization code grant 방식으로 인가를 못시켜서.. 이또한.. oauth2Login 에 의한 redirect 되어 인가및 인증되게 됨..(의도에서 벗어남)
         * 3. "/authentication" 를 호출하여 oauth2Login 으로 인가 및 인증 하고 "/authorized-client-password" 를 호출하면..
         *      oauth2Login 에서는 my-keycloak registration id 로 인가 및 인증 처리를 하였기 때문에..
         *      @RegisteredOAuth2AuthorizedClient 에 "my-keycloak-password-credentials" 로 서로 다른 registration id 라..
         *      ClientRegistration 를 찾지 못한다.
         * 4. "/authorized-client" 와 마찬가지로 인증 처리를 하지 않는다.
         */

        ClientRegistration clientRegistration = authorizedClient.getClientRegistration();
        OAuth2AccessToken accessToken = authorizedClient.getAccessToken();
        OAuth2RefreshToken refreshToken = authorizedClient.getRefreshToken();

        log.info("clientRegistration: {}", clientRegistration);
        log.info("accessToken: {}", accessToken.getTokenValue());
        log.info("refreshToken: {}", refreshToken.getTokenValue());

        return authorizedClient;
    }

    @GetMapping("/authorized-client-password/authenticate")
    public String authenticate(
            @RegisteredOAuth2AuthorizedClient("my-keycloak-password-credentials") OAuth2AuthorizedClient authorizedClient,
            HttpServletRequest request,
            HttpServletResponse response
    ) {

        /**
         * "/authorized-client-password" 와 마찬가지로 username, password 쿼리파라미터를 추가하고 호출해야한다.
         * HelloController 의 "/authorized-client-password" path controller 에 인증 처리까지 추가해봄
         */

        if (authorizedClient != null) {
            OAuth2UserRequest userRequest = new OAuth2UserRequest(
                    authorizedClient.getClientRegistration(),
                    authorizedClient.getAccessToken()
            );

            OAuth2User oAuth2User = userService.loadUser(userRequest); // userinfo

            // 인증 객체 생성
            SimpleAuthorityMapper mapper = new SimpleAuthorityMapper();
            Set<GrantedAuthority> grantedAuthorities = mapper.mapAuthorities(oAuth2User.getAuthorities());
            OAuth2AuthenticationToken authenticationToken = new OAuth2AuthenticationToken(oAuth2User, grantedAuthorities, authorizedClient.getClientRegistration().getRegistrationId());

            // authorizedClient 저장, OAuth2AuthorizedClientArgumentResolver 에서 OAuth2AuthorizedClientManager 에 의해 저장되지만.. 당시에는 인증객체가 anonymous 라 저장이 안되서 직접 다시 저장해줘야한다.
            authorizationSuccessHandler.onAuthorizationSuccess(
                    authorizedClient,
                    authenticationToken,
                    Map.of(
                            HttpServletRequest.class.getName(), request,
                            HttpServletResponse.class.getName(), response
                    )
            );

            // 인증 후, 인증 객체를 세션에 저장한다. (쿠키 세션 기반 로그인 유지를 위함)
            SecurityContext existingSecurityContext = SecurityContextHolder.getContextHolderStrategy().getContext(); // 기존의 securityContext 는 anonymous 이다.
            SecurityContext securityContext = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
            securityContext.setAuthentication(authenticationToken);
            securityContextRepository.saveContext(securityContext, request, response);

            return "Authentication successful";
        }

        return "Authentication failed";
    }
}
