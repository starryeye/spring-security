package dev.starryeye.custom_oauth2_client_oauth2_client_configurer;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequiredArgsConstructor
public class HelloController {

    private final OAuth2AuthorizedClientRepository authorizedClientRepository;

    @GetMapping("/authorize")
    public String authorize() {
        return "redirect:/oauth2/authorization/my-keycloak"; // 1단계 호출
    }

    @GetMapping("/hello")
    @ResponseBody
    public String hello(HttpServletRequest request) {

        /**
         * "/authorize" 를 호출해서 여기에 도달했다는 것은..
         * authorization server 를 통해 사용자가 client server 에게 사용자 리소스 접근 권한을 부여했다는 의미이다. (access token 발급)
         *
         * 참고.
         * client server 가 사용자 대신 resource server 에 사용자의 resource 를 접근 할 수 있는 권한이 생긴 것이지..
         * 사용자가 client server 에 인증을 하진 않았음을 잘 생각해야한다.
         * 따라서, /authorize, /hello path 에 permitAll() 을 적용시켰고..
         * 당연하게도.. client server 는 해당 사용자를 anonymousUser 로 본다.
         */

        Authentication authentication = SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();

        // 아래 객체에 대한 설명은.. git, oauth2-authorized-client project 참고
        OAuth2AuthorizedClient oAuth2AuthorizedClient = authorizedClientRepository.loadAuthorizedClient("my-keycloak", authentication, request);

        return "access token = " + oAuth2AuthorizedClient.getAccessToken().getTokenValue();
    }
}
