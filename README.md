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
- 공통 아키텍처
  - custom-authenticate-exception-handling
    - 인증/인가 예외 처리, ExceptionTranslationFilter
  - custom-security-matcher
- 인가 처리
  - custom-authorize-authorize-http-requests
  - custom-authorize-request-matcher
- 인가 아키텍처
  - custom-authorize-granted-authority
    - GrantedAuthority, Authentication
- 기타
  - custom-authenticate-login-controller
    - MVC controller 에서 인증을 처리해보기, like formLogin
  - custom-cors
    - CorsFilter, CORS 보안 설정
  - custom-csrf
  - custom-csrf-2
