package dev.starryeye.argument_resolver;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizationSuccessHandler;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;

@RestController
public class AdditionController {


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

    @GetMapping("/authorized-client/authenticate")
    public String authenticate(
            @RegisteredOAuth2AuthorizedClient("my-keycloak") OAuth2AuthorizedClient authorizedClient,
            HttpServletRequest request,
            HttpServletResponse response
    ) {

        /**
         * HelloController 의 "/authorized-client" path controller 에 인증 처리까지 추가해봄
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
            SecurityContext securityContext11 = SecurityContextHolder.getContextHolderStrategy().getContext(); //?
            SecurityContext securityContext = SecurityContextHolder.getContextHolderStrategy().createEmptyContext();
            securityContext.setAuthentication(authenticationToken);
            securityContextRepository.saveContext(securityContext, request, response);

            return "Authentication successful";
        }

        return "Authentication failed";
    }
}
