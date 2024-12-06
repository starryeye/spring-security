# spring-security
- [spring security documentation](https://docs.spring.io/spring-security/reference/index.html)
- [spring security milestones](https://github.com/spring-projects/spring-security/milestones)
- 


## projects
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

- 공통 아키텍처
  - custom-authenticate-exception-handling
    - 인증/인가 예외 처리, ExceptionTranslationFilter
  - custom-security-matcher
    - securityMatchers, FilterChainProxy, SecurityFilterChain

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
  - custom-authorize-authorization-manager-2
   
