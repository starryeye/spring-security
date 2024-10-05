package dev.starryeye.custom_authorize_pre_post_authorize.controller;

import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    /**
     * @PreAuthorize, @PostAuthorize
     * - 메서드 수준에서의 권한 부여
     *      따라서, Controller 계층 뿐만 아니라 Service 계층과 같이 어떤 메서드에서 모두 사용가능하다.
     * - 설정 클래스에 @EnableMethodSecurity 어노테이션이 필요하다.
     * - SpEL(spring Expression Language) 표현식을 사용할 수 있다.
     * - 컨트롤러 레이어에서는 요청기반 권한 검사 방법을 사용하도록하고.. 서비스레이어에서 사용하도록하자..
     *
     * @PreAuthorize
     * - 메서드가 실행되기 전에 특정한 보안 조건이 충족되는지 확인하고 메서드를 실행
     *
     * @PostAuthorize
     * - 메서드가 실행된 후에 보안 조건이 충족되는지 확인하고 최종 리턴한다.
     *
     */

    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')") // 권한 체크
    public String admin() {
        return "admin";
    }

    @GetMapping("/user")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_USER')") // 권한 체크
    public String user() {
        return "user";
    }

    @GetMapping("/isAuthenticated")
    @PreAuthorize("isAuthenticated()") // 인증 여부
    public String isAuthenticated() {
        return "isAuthenticated";
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("#id == authentication.name") // 인증객체의 name 과 동일한 path variable 이어야 접근
    public String authentication(@PathVariable(value = "id") String id) {
        return "Hello " + id;
    }

    @GetMapping("/owner")
    @PostAuthorize("returnObject.owner() == authentication.name") // 인증객체의 name 과 동일한 값의 owner 필드가 존재하는 리턴 객체이면 리턴됨
    public Account owner(
            @RequestParam(value = "name") String name
    ) {
        return new Account(name, false);
    }

    @GetMapping("/isSecure")
    @PostAuthorize("hasAuthority('ROLE_ADMIN') and returnObject.secure") // ADMIN 권한이 존재하고 리턴 객체의 isSecure 값이 true 이어야 리턴됨
    public Account isSecure(
            Authentication authentication,
            @RequestParam(value = "isSecure") String isSecure
    ) {
        return new Account(authentication.getName(), "Y".equals(isSecure));
    }
}
