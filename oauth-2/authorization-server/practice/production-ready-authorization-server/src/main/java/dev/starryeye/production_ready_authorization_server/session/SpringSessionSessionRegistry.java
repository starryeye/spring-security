package dev.starryeye.production_ready_authorization_server.session;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;

import java.time.Instant;
import java.util.Date;
import java.util.List;

public class SpringSessionSessionRegistry implements SessionRegistry {

    /**
     * spring session(redis)을 진실의 원천으로 삼는 SessionRegistry 구현이다.
     *
     * 왜 필요한가..
     *      spring authorization server 는 openid scope 의 token 요청 시 SessionRegistry 에서 로그인 세션을 찾아
     *      id token 에 sid(세션 해시)와 auth_time(SessionInformation.getLastRequest())을 넣는다. (JwtGenerator)
     *      SessionRegistry 빈이 없으면 인스턴스별 InMemory(SessionRegistryImpl)가 쓰이는데..
     *      로그인 세션은 로그인을 처리한 인스턴스의 registry 에만 등록되므로,
     *      LB 뒤 다중 인스턴스에서는 같은 사용자의 id token 에 인스턴스마다 서로 다른 auth_time 이 들어가게 된다.
     *      재인증이 없는 한 auth_time 은 같은 값이어야 하므로 OIDC 스펙 위반이다.
     *      (단일 인스턴스에서는 드러나지 않는 결함.. 검증 기록은 openid-conformance/README.md 참고)
     *
     * 해결..
     *      세션 자체가 이미 redis(spring session)에 공유되어 있으므로, registry 조회를 spring session 저장소로 위임한다.
     *      principal 로 세션을 찾으려면 인덱스가 필요해서 spring.session.redis.repository-type=indexed 설정이 전제된다.
     *      (spring session 도 SpringSessionBackedSessionRegistry 를 제공하지만.. 그 구현은 lastRequest 로
     *       lastAccessedTime(요청마다 갱신)을 반환하므로 auth_time 이 여전히 발급 시점마다 달라진다.
     *       auth_time 은 "사용자가 인증한 시각" 이어야 하므로, 로그인 성공 시 세션에 기록해둔 시각을 반환하도록 직접 구현했다)
     *
     * 등록/삭제 계열이 no-op 인 이유..
     *      세션 생명주기는 spring session 이 관리하는 것이 진실이고, 여기는 조회 전용 뷰이기 때문이다.
     */

    public static final String AUTH_TIME_ATTRIBUTE = "my.authTime"; // 로그인 성공 시각 (DefaultSecurityConfig 의 success handler 가 기록)

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;

    public SpringSessionSessionRegistry(FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Override
    public List<SessionInformation> getAllSessions(Object principal, boolean includeExpiredSessions) {
        // 만료 세션은 spring session 이 스스로 정리하므로 includeExpiredSessions 는 고려할 것이 없다.
        return sessionRepository.findByPrincipalName(resolvePrincipalName(principal)).values().stream()
                .map(this::toSessionInformation)
                .toList();
    }

    @Override
    public SessionInformation getSessionInformation(String sessionId) {
        Session session = sessionRepository.findById(sessionId);
        return session != null ? toSessionInformation(session) : null;
    }

    @Override
    public List<Object> getAllPrincipals() {
        // 전체 principal 열거는 지원하지 않는다. (authorization server 동작에 불필요)
        return List.of();
    }

    @Override
    public void refreshLastRequest(String sessionId) {
        // no-op
    }

    @Override
    public void registerNewSession(String sessionId, Object principal) {
        // no-op.. spring authorization server 가 로그인 시 등록을 시도하지만 저장은 spring session 몫이다.
    }

    @Override
    public void removeSessionInformation(String sessionId) {
        // no-op
    }

    private SessionInformation toSessionInformation(Session session) {

        // lastRequest 자리에 "로그인 시각" 을 담는다.. JwtGenerator 가 이 값을 id token 의 auth_time 으로 사용한다.
        Instant authTime = session.getAttribute(AUTH_TIME_ATTRIBUTE);
        Date authenticatedAt = Date.from(authTime != null ? authTime : session.getCreationTime());

        return new SessionInformation(resolvePrincipalName(session), session.getId(), authenticatedAt);
    }

    /**
     * 세션에서 principal 이름을 얻는다..
     *      주의. PRINCIPAL_NAME_INDEX_NAME 은 인덱스 계산에 쓰이는 이름일 뿐 세션 attribute 로 저장되지는 않는다. (조회하면 null)
     *      그래서 세션에 저장된 SecurityContext(SPRING_SECURITY_CONTEXT)에서 인증 이름을 꺼낸다.
     */
    private String resolvePrincipalName(Session session) {
        SecurityContext securityContext = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (securityContext != null && securityContext.getAuthentication() != null) {
            return securityContext.getAuthentication().getName();
        }
        return "unknown"; // 미인증 세션.. SessionInformation 의 principal 은 null 이 허용되지 않는다.
    }

    private String resolvePrincipalName(Object principal) {
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername();
        }
        return principal.toString();
    }
}
