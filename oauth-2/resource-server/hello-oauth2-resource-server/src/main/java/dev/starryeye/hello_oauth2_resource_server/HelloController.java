package dev.starryeye.hello_oauth2_resource_server;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    /**
     * 현재, "/" 에 access 하기 위해서는 인증이 필요하다.
     * 해당 프로젝트는 resource-server 로써 authorization server 로 통신이 가능하다.
     *      해당 "/" 요청의 권한을 승인하는 절차를 authorization server 로 위임할 수 있음.
     *      요청 시, Authorization header 에 "bearer {access token}" 값을 함께 넣어주면.. (api.http 참고)
     *          authorization server 를 통해 해당 access token 에 대하여 검증을 수행하고
     *          최종 인증 처리를 한다.
     */

    @GetMapping("/")
    public Authentication hello(Authentication authentication) {

        return authentication;
    }
}
