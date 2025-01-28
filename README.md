# spring-security
- [spring security documentation](https://docs.spring.io/spring-security/reference/index.html)
- [spring security milestones](https://github.com/spring-projects/spring-security/milestones)
- 

## 실전 베이스 projects
- practices/auth-service

## 이론 베이스 projects
- basic
  - default-security
    - spring security 라이브러리와 기본 설정
  - custom-filter
    - SecurityFilterChain 등록

- 인증 방법 및 인증 처리
  - custom-authenticate-form-login
    - 폼인증과 설정
  - custom-authenticate-http-basic
    - HTTP basic 인증과 설정
  - custom-authenticate-remember-me
    - rememberMe 설정
  - custom-authenticate-anonymous
    - anonymous 설정
  - custom-authenticate-logout
    - logout 설정

- 인증 공통 아키텍처
  - custom-authenticate-authentication-manager
    - AuthenticationManager, ProviderManager
  - custom-authenticate-authentication-provider
    - AuthenticationProvider
  - custom-authenticate-user-details-service
    - UserDetails, UserDetailsService
  - custom-authenticate-security-context
    - SecurityContextHolderFilter, SecurityContextRepository
  - custom-authenticate-session-management
    - Session 관리 및 제어
  - custom-authenticate-session-registry
    - SessionRegistry, SecurityContextRepository

- 인증 기타
  - custom-authenticate-login-controller
    - MVC controller 에서 인증을 처리해보기, like formLogin
  - custom-authenticate-servlet-integration
    - 서블릿과 통합하여 서블릿에서 다양한 인증 작업을 처리할 수 있다.
    - SecurityContextHolderAwareRequestFilter, HttpServlet3RequestFactory, Servelt3SecurityContextHolderAwareRequestWrapper
  - custom-authenticate-mvc-integration
    - @CurrentSecurityContext, @AuthenticationPrincipal, AuthenticationPrincipalArgumentResolver
    - SecurityContextHolder, SecurityContext, Authentication, Principal, UserDetails, User
    - AuthenticationTrustResolver
  - custom-authenticate-mvc-integration-2
    - WebAsyncManagerIntegrationFilter, WebAsyncManager, SecurityContextCallableProcessionInterceptor, SecurityContext
    - Callable, @Async, CompletableFuture, ThreadLocal
    - SecurityContextHolder.MODE_INHERITABLETHREADLOCAL

- 보안 처리
  - custom-cors
    - CorsFilter, CORS 보안 설정
  - custom-csrf
    - CsrfFilter, CsrfTokenRepository, CsrfTokenRequestHandler, CSRF 보안 설정
  - custom-csrf-2
    - HTML form/JavaScript 에 CSRF 적용해보기, CsrfFilter, CsrfTokenRepository, CsrfTokenRequestHandler
  - custom-session-same-site
    - Spring-session 을 통해 SameSite 기술 이용

- 인가 처리
  - 요청 기반 권한설정
    - custom-authorize-authorize-http-requests
      - authorizeHttpRequests 설정
    - custom-authorize-access
      - authorizeHttpRequests 설정, 표현식이용, access, AuthorizationManager, ExpressionHandler
    - custom-authorize-request-matcher
      - authorizeHttpRequests 설정, requestMatchers, RequestMatcher
    - custom-authorize-ignoring-and-permit-all
      - 정적자원은 모두 보안 검사하지 않도록 해보기, WebSecurityCustomizer, ignoring, requestMatcher, permitAll
  - 메서드 기반 권한설정(AOP 기반)
    - custom-authorize-pre-post-authorize
      - @EnableMethodSecurity, @PreAuthorize, @PostAuthorize
    - custom-authorize-pre-post-filter
      - @EnableMethodSecurity, @PreFilter, @PostFilter
  - cusom-authorize-role-hierarchy
    - RoleHierarchy

- 인가 공통 아키텍처
  - custom-authorize-granted-authority
    - GrantedAuthority, Authentication
  - 요청 기반 권한
    - custom-authorize-authorization-manager
      - AuthorizationFilter, AuthorizationManager, RequestMatcherDelegatingAuthorizationManager
      - AuthenticatedAuthorizationManager, AuthorityAuthorizationManager, WebExpressionAuthorizationManager
      - custom AuthorizationManager
    - custom-authorize-request-matcher-delegating-authorization-manager
      - custom RequestMatcherDelegatingAuthorizationManager
  - 메서드 기반 권한(AOP 기반)
    - custom-authorize-authorization-manager-2
      - custom AuthorizationManager
      - custom Pointcut
    - custom-authorize-method-interceptor
      - custom MethodInterceptor
      - Advisor, MethodInterceptor, AuthorizationAdvisor
        - AuthorizationManager(Before/After)MethodInterceptor, (Pre/Post)AuthorizationMethodInterceptor
      - AuthorizationManager
        - (Pre/Post)AuthorizeAuthorizationManager, SecuredAuthorizationManager, Jsr250AuthorizationManager, AuthenticatedAuthorizationManager

- 인증/인가 공통 아키텍처
  - custom-authenticate-exception-handling
    - 인증/인가 예외 처리, ExceptionTranslationFilter
  - custom-security-matcher
    - securityMatchers, FilterChainProxy, SecurityFilterChain
  - custom-multiple-security-filter-chain
    - WebSecurity, FilterChainProxy, SecurityFilterChain
    - securityMatchers
  - custom-redis-session
    - spring-session-data-redis, spring-data-redis
    - HttpSessionSecurityContextRepository, HttpSessionRequestCache, RedisConnectionFactory

- 인증/인가 이벤트
  - custom-authenticate-authentication-event
    - ApplicationEvent, AbstractAuthenticationEvent, AbstractAuthenticationFailureEvent, AuthenticationSuccessEvent
    - ApplicationEventPublisher, AuthenticationEventPublisher
    - @EventListener
    - custom AbstractAuthenticationEvent
  - custom-authenticate-authentication-event-publisher
    - custom AuthenticationEventPublisher 만들어보기
  - custom-authorize-authorization-event
    - AuthorizationEvent, AuthorizationDeniedEvent, AuthorizationGrantedEvent
    - AuthorizationEventPublisher, SpringAuthorizationEventPublisher
    - custom AuthorizationEventPublisher 만들어보기

## posting
- [Spring security 소개](https://starryeye.tistory.com/235)
- [Spring security 초기화 1](https://starryeye.tistory.com/236)
- [Spring security 초기화 2](https://starryeye.tistory.com/237)
