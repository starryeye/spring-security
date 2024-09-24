package dev.starryeye.custom_authenticate_session_registry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionInfoService {

    private final SessionRegistry sessionRegistry;
    private final DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public void sessionInfo() {

        /**
         * SessionRegistry api..
         * - List<Object> getAllPrincipals();
         *      현재 생성된 모든 사용자 정보를 가지고 온다.
         * - List<SessionInformation> getAllSessions(Object principal, boolean includeExpiredSessions)
         *      파라미터로 전달된 사용자(principal) 의 세션을 가지고 온다. (includeExpiredSessions 를 false 로 두면 만료된 세션은 포함하지 않음)
         *
         * 참고
         * 여기서의 Object principal 이 UserDetails 를 구현한 User 인스턴스이며
         * 해당 principal 로 구한 SessionInformation 은 principal 을 다양한 정보와 함께 한번더 감싼 클래스이고
         * SessionInformation 으로 구한 세션 Id 는 클라이언트로 전달한 쿠키 값이다. (JSESSIONID)
         *
         * 참고
         * AuthenticationProvider 는 username 을 바탕으로 UserDetailsService 를 통해 UserDetails 를 조회하고(인증)
         * AuthenticationProvider 는 인증에 성공하면 UserDetails 로 인증에 성공한 Authentication 객체를 생성한다.
         * formLogin 기준으로 UsernamePasswordAuthenticationFilter 는 AuthenticationManager 로 부터 인증에 성공한 Authentication 객체를 받아서
         *      SecurityContextRepository 를 이용해 Authentication 이 포함된 SecurityContext 를 HttpSession 에 저장한다.
         *      동시에, 요청 단위로 공유될 수 있도록(ThreadLocal) SecurityContextHolder 에도 SecurityContext 를 저장한다.
         */

        sessionRegistry.getAllPrincipals().stream()
                .flatMap(principal ->
                        sessionRegistry.getAllSessions(principal, false).stream()
                                .map(session -> Map.entry(principal, session))
                )
                .forEach(entry -> printSessionInfo(entry.getKey(), entry.getValue()));
    }

    private void printSessionInfo(Object principal, SessionInformation sessionInformation) {
        LocalDateTime lastRequestTime = convertToLocalDateTime(sessionInformation.getLastRequest());
        log.info("사용자 정보: {} | 세션 ID: {} | 최종 요청 시간: {}",
                principal,
                sessionInformation.getSessionId(),
                dateTimeFormatter.format(lastRequestTime));
    }

    private LocalDateTime convertToLocalDateTime(java.util.Date date) {
        return Instant.ofEpochMilli(date.getTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }
}
