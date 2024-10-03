package dev.starryeye.custom_session_same_site;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.SessionRepository;
import org.springframework.session.config.annotation.web.http.EnableSpringHttpSession;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableSpringHttpSession // 설정을 해줘야 Spring Session 에서 Session 을 관리 적용됨.
public class SessionConfig {

    /**
     * Spring Session 에서 Session 을 관리하겠다.
     * -> Tomcat 같은 WAS 에서 쿠키 세션을 관리하지 않고 Spring Session 에서 관리한다.
     * JSESSIONID 이라는 이름으로 쿠키가 발급되지 않고 SESSION 이라는 이름으로 발급
     */

    @Bean
    public CookieSerializer cookieSerializer() {

        /**
         * 쿠키를 만들 때 참고되는 설정 객체
         * Http Session Cookie 를 의미하며 다른 쿠키를 만들땐 해당되지 않음
         */

        DefaultCookieSerializer cookieSerializer = new DefaultCookieSerializer();
        cookieSerializer.setCookieName("SESSION"); // Session Id 이름 설정 (기본)
        cookieSerializer.setUseSecureCookie(true); // 쿠키를 보안 쿠키로 사용
        cookieSerializer.setUseHttpOnlyCookie(true); // Http 통신에서만 사용 (기본)
        cookieSerializer.setSameSite("Lax"); // same site 설정 (기본), (Strict, Lax, None)

        return cookieSerializer;
    }

    @Bean
    public SessionRepository<MapSession> sessionRepository() {

        /**
         * 세션을 저장하는 저장소 객체
         */

        return new MapSessionRepository(new ConcurrentHashMap<>());
    }
}
