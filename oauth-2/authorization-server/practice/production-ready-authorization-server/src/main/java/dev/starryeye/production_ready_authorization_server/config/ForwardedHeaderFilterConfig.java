package dev.starryeye.production_ready_authorization_server.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Configuration
public class ForwardedHeaderFilterConfig {

    /**
     * X-Forwarded-* 헤더를 요청 객체에 반영해주는 ForwardedHeaderFilter 를 등록한다. (etc/forwarded-header-filter 프로젝트 이식)
     *      이 프로젝트는 항상 nginx 뒤에 있으므로 출처 프로젝트와 달리 조건부(@ConditionalOnProperty) 없이 상시 등록한다.
     *
     * issuer 를 고정(AuthorizationServerConfig)했더라도 이 filter 는 필요하다..
     *      로그인 redirect 등 요청 기반으로 만들어지는 URL 이 남아 있어서..
     *      없으면 Location 헤더에 LB 주소(localhost:9000) 대신 내부 주소(host.docker.internal:8091 등)가 노출된다.
     */
    @Bean
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {

        FilterRegistrationBean<ForwardedHeaderFilter> registrationBean = new FilterRegistrationBean<>(new ForwardedHeaderFilter());
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return registrationBean;
    }
}
