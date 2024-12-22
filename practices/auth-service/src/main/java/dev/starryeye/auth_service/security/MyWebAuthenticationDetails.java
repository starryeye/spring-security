package dev.starryeye.auth_service.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Getter;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

@Getter
public class MyWebAuthenticationDetails extends WebAuthenticationDetails {

    private final String secretKey;

    public MyWebAuthenticationDetails(HttpServletRequest request) {
        /**
         * WebAuthenticationDetails(MyWebAuthenticationDetails) 는
         * 인증 요청시점에 AuthenticationFilter 에 의해 Authentication(인증 객체) 내부에 저장되며
         * 기본적으로 Client 의 IP 주소와 sessionId 등을 저장하며
         * 커스텀으로 작성시.. 아래와 같이 요청 데이터의 특정 값들을 추출하여 함께 저장할 수 있다.
         */
        super(request);
        // Authentication 내부 WebAuthenticationDetails 에 요청 데이터 중.. 쿼리 파라미터의 secret_key 값을 추출하여 저장해놓는다.
        // 저장해놓은 값은 MyAuthenticationProvider 에서 인증 시 사용하도록함.
        this.secretKey = request.getParameter("secret_key");
    }
}
