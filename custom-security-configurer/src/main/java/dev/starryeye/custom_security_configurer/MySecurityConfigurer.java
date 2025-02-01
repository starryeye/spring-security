package dev.starryeye.custom_security_configurer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;

@Slf4j
public class MySecurityConfigurer extends AbstractHttpConfigurer<MySecurityConfigurer, HttpSecurity> {

    /**
     * HttpSecurity(SecurityBuilder) 에 SecurityConfigurer 를 적용시킨다. (SecurityConfig 참고)
     * HttpSecurity(SecurityBuilder) 가 build 메서드를 호출하면, SecurityConfigurer 클래스의 init, configure 메소드를 호출하게되고
     * SecurityConfigurer 는 filter 및 해당 filter 의 역할에 필요한 객체들을 초기화 한다.
     * 만들어진 filter 는 최종적으로 SecurityFilterChain 가 관리하는 filter 중 일부가 된다.
     */

    private boolean isSecure;

    @Override
    public void init(HttpSecurity builder) throws Exception {
        super.init(builder);
        log.info("MySecurityConfigurer init method called");
    }

    @Override
    public void configure(HttpSecurity builder) throws Exception {
        super.configure(builder);
        log.info("MySecurityConfigurer configure method called");

        if (isSecure) {
            log.info("https is required");
        } else {
            log.info("http is optional");
        }
    }

    public MySecurityConfigurer isSecure(boolean isSecure) {
        this.isSecure = isSecure;
        return this;
    }
}
