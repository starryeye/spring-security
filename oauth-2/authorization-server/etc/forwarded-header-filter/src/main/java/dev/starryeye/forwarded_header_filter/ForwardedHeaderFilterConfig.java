package dev.starryeye.forwarded_header_filter;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ForwardedHeaderFilter;

@Configuration
public class ForwardedHeaderFilterConfig {

    /**
     * X-Forwarded-* 헤더를 요청 객체에 반영해주는 ForwardedHeaderFilter 를 등록한다.
     *      security filter chain 을 포함한 모든 처리가 재구성된 요청 정보를 보도록 최우선 순위로 등록한다.
     *
     * my.forwarded-header-filter.enabled=false 로 바꿔 재기동하면..
     *      프록시를 거친 요청의 issuer 가 내부 주소로 잘못 나오는 문제를 재현할 수 있다. (main class 확인 포인트 3)
     *
     * 참고. server.forward-headers-strategy=framework 설정으로도 spring boot 가 동일한 filter 를 자동 등록해준다.
     *      (또 다른 값인 native 는 tomcat 의 RemoteIpValve 가 처리하는 방식이다)
     */
    @Bean
    @ConditionalOnProperty(name = "my.forwarded-header-filter.enabled", havingValue = "true")
    public FilterRegistrationBean<ForwardedHeaderFilter> forwardedHeaderFilter() {

        FilterRegistrationBean<ForwardedHeaderFilter> registrationBean = new FilterRegistrationBean<>(new ForwardedHeaderFilter());
        registrationBean.setOrder(Ordered.HIGHEST_PRECEDENCE);

        return registrationBean;
    }
}
