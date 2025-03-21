package dev.starryeye.oauth2_authorized_client_manager_and_filter;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizationSuccessHandler;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class MyOAuth2LoginAuthenticationFilter extends AbstractAuthenticationProcessingFilter {

    /**
     * OAuth2AuthorizedClientManager 를 이용하여
     * Resource owner password credentials grant 방식으로 인가를 하고 인증까지 처리하는..
     * 커스텀 필터를 만들어본다.
     *
     * 참고
     * AuthenticationManager 에 의한 인증을 수행하지 않고 있음
     *
     * todo
     *      현재 로그인 url(/password-credentials-grant-login) 로 요청하면,
     *          password grant 로 인가를 받고 DefaultOAuth2UserService 를 통해 userinfo 요청하여 인증을 받고 있다..
     *      인증 객체 및 SecurityContext 를 HttpSessionSecurityContextRepository 에 저장하여 결국 쿠키 세션으로 로그인이 유지되고 있다..
     *      id token 으로 로그인 유지를 해야하는 건 아닌지...(하다 못해.. access token 으로 라도..)
     *      로그아웃 처리..
     *
     */

    public static final String DEFAULT_FILTER_PROCESSES_URI = "/password-credentials-grant-login/**";

    private final DefaultOAuth2AuthorizedClientManager authorizedClientManager;

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> oauth2UserService;
    private final OAuth2AuthorizationSuccessHandler successHandler;

    public MyOAuth2LoginAuthenticationFilter(
            DefaultOAuth2AuthorizedClientManager authorizedClientManager,
            OAuth2AuthorizedClientRepository authorizedClientRepository
    ) {
        super(DEFAULT_FILTER_PROCESSES_URI);
        this.authorizedClientManager = authorizedClientManager;

        this.oauth2UserService = new DefaultOAuth2UserService();
        this.successHandler = (authorizedClient, authentication, attributes) -> {
            authorizedClientRepository.saveAuthorizedClient(
                    authorizedClient,
                    authentication,
                    (HttpServletRequest) attributes.get(HttpServletRequest.class.getName()),
                    (HttpServletResponse) attributes.get(HttpServletResponse.class.getName())
            );
        };

        this.authorizedClientManager.setAuthorizationSuccessHandler(successHandler);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException, ServletException {

        Authentication authentication = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();

        if (authentication == null) {
            authentication = new AnonymousAuthenticationToken("anonymous", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        }

        OAuth2AuthorizeRequest oAuth2AuthorizeRequest = OAuth2AuthorizeRequest.withClientRegistrationId("my-keycloak-password-credentials")
                .principal(authentication)
                .attribute(HttpServletRequest.class.getName(), request)
                .attribute(HttpServletResponse.class.getName(), response)
                .build();

        // Resource owner password credentials grant 방식으로 인가 처리
        OAuth2AuthorizedClient authorizedClient = authorizedClientManager.authorize(oAuth2AuthorizeRequest);

        // access token, userinfo 로 인증 처리
        if (authorizedClient != null) {

            OAuth2UserRequest oAuth2UserRequest = new OAuth2UserRequest(authorizedClient.getClientRegistration(), authorizedClient.getAccessToken());

            OAuth2User oAuth2User = oauth2UserService.loadUser(oAuth2UserRequest); // userinfo

            // 인증 객체 생성
            SimpleAuthorityMapper mapper = new SimpleAuthorityMapper();
            Set<GrantedAuthority> grantedAuthorities = mapper.mapAuthorities(oAuth2User.getAuthorities());
            OAuth2AuthenticationToken authenticationToken = new OAuth2AuthenticationToken(oAuth2User, grantedAuthorities, authorizedClient.getClientRegistration().getRegistrationId());

            successHandler.onAuthorizationSuccess(
                    authorizedClient,
                    authenticationToken,
                    Map.of(
                            HttpServletRequest.class.getName(), request,
                            HttpServletResponse.class.getName(), response
                    )
            );

            return authenticationToken;
        }

        return authentication;
    }
}
