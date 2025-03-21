package dev.starryeye.oauth2_authorized_client;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Set;

@Controller
@RequiredArgsConstructor
public class ClientLoginController {

    private static final String CLIENT_REGISTRATION_ID = "my-keycloak";

    private final OAuth2AuthorizedClientRepository authorizedClientRepository;
    private final OAuth2AuthorizedClientService authorizedClientService;

    /**
     * OAuth2AuthorizedClient..
     *      사용자가 client 에게 리소스 접근 권한을 부여했을 때 만들어지는 객체이다. (인가를 받은 client 라는 의미)
     *      access token, refresh token, ClientRegistration, principalName 등이 적재되어있음.
     *
     * OAuth2AuthorizedClientRepository 는 OAuth2AuthorizedClientService 를 사용하여 OAuth2AuthorizedClient 를 관리(CRD) 한다.
     *
     * OAuth2AuthorizedClientRepository, OAuth2AuthorizedClientService ..
     *      기본적으로 Spring bean 으로 등록된다. (OAuth2WebSecurityConfiguration)
     *      개발자가 OAuth2AuthorizedClientRepository, OAuth2AuthorizedClientService 를 이용하여 access token 을 참조 할 수 있다.
     *          access token 으로 resource server 에 접근하여 resource 를 얻을 수 있다.
     *      oauth2Login() 을 설정하면..
     *          2단게 필터인 OAuth2LoginAuthenticationFilter 에서 OAuth2AuthorizedClientRepository 에 OAuth2AuthorizedClient 를 저장함
     *      oauth2Client() 를 설정하면..
     *          2단계 필터인 OAuth2AuthorizationCodeGrantFilter 에서 OAuth2AuthorizedClientRepository 에 OAuth2AuthorizedClient 를 저장함
     *
     * 참고.
     * 현재 사용자가 client 에게 anonymous 라면..
     *      OAuth2AuthorizedClientService 로 부터 OAuth2AuthorizedClient 를 참조하면 null 이 리턴된다.
     */

    /**
     * oauth2Client() api 를 통해 사용자가 client server 에게 사용자의 리소스 접근 권한을 부여하도록하고
     * /client-login controller 를 통해..
     *      인가된 client server 가 access token 으로 resource server (여기서는 keycloak userinfo 이용) 에 접근해서
     *      사용자 정보를 획득하고 인증 처리해본다.
     *      즉, 로직 자체는 oauth2Client() api 에서 제공하지 않는 인증처리를 해당 "/login" controller 에서 처리해보는 연습이다.
     *          oauth2Login() api 의 OAuth2LoginAuthenticationFilter 로직을 참고함..
     *
     * todo, 로그인 유지는 되고 있는가?
     */

    @GetMapping("/client-login")
    public String login(HttpServletRequest request, Model model) {

        Authentication authentication = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();

        // OAuth2AuthorizedClient 접근 방법 1
        OAuth2AuthorizedClient oAuth2AuthorizedClient1 = authorizedClientRepository.loadAuthorizedClient(CLIENT_REGISTRATION_ID, authentication, request);

        // OAuth2AuthorizedClient 접근 방법 2
        OAuth2AuthorizedClient oAuth2AuthorizedClient2 = authorizedClientService.loadAuthorizedClient(CLIENT_REGISTRATION_ID, authentication.getName());

        // access token 접근
        OAuth2AccessToken accessToken = oAuth2AuthorizedClient1.getAccessToken();

        // ClientRegistration 접근
        ClientRegistration clientRegistration = oAuth2AuthorizedClient1.getClientRegistration();

        // access token 으로 userinfo 호출
        OAuth2UserService<OAuth2UserRequest, OAuth2User> oAuth2UserService = new DefaultOAuth2UserService();
        OAuth2User oAuth2User = oAuth2UserService.loadUser(new OAuth2UserRequest(clientRegistration, accessToken)); // userinfo 요청

        // 인증 처리
        SimpleAuthorityMapper authorityMapper = new SimpleAuthorityMapper();
        authorityMapper.setPrefix("CUSTOM_");
        authorityMapper.setDefaultAuthority("OAUTH2_USER");
        Set<GrantedAuthority> grantedAuthorities = authorityMapper.mapAuthorities(oAuth2User.getAuthorities());
        OAuth2AuthenticationToken authenticationToken = new OAuth2AuthenticationToken( // 인증 객체 생성
                oAuth2User,
                grantedAuthorities,
                CLIENT_REGISTRATION_ID
        );
        SecurityContextHolder.getContextHolderStrategy().getContext().setAuthentication(authenticationToken); // 인증 객체 적재


        // 화면 출력
        model.addAttribute("accessToken", accessToken.getTokenValue());
        model.addAttribute("refreshToken", oAuth2AuthorizedClient1.getRefreshToken().getTokenValue());
        model.addAttribute("principalName", oAuth2AuthorizedClient1.getPrincipalName());
        model.addAttribute("clientName", oAuth2AuthorizedClient1.getClientRegistration().getClientName());


        return "client-login";
    }
}
