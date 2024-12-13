package dev.starryeye.custom_authenticate_mvc_integration.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.*;

@Target({ ElementType.PARAMETER, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@AuthenticationPrincipal(expression = "#this == 'anonymousUser' ? 'anonymous' : username")
public @interface CustomAuthenticationPrincipalUsername {
    /**
     * @AuthenticationPrincipal 을 래핑한 어노테이션을 만들었다.
     * expression 요소를 통해..
     *      anonymous 라면 anonymous 로 사용
     *      인증사용자면 username 을 사용
     *
     * 여기서의 this 는 Authentication 의 principal 이다.
     * 따라서, 익명사용자일 경우 this 가 "anonymousUser" 문자열 일것이고..
     * 인증사용자일 경우..
     *      this 가 principal 이며.. username 은 principal 의 필드이다.
     */
}
