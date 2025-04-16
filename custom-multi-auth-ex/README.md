## 설명

### 프로젝트 의의
- multi SecurityFilterChain 관리
- 다중 AuthenticationManager 관리
- (stateless vs session-based) authentication

### api 설명
- "/admin"
  - SecurityFilterChain(adminChain) 으로 동작함.
  - AuthenticationManager : apiKeyAuthenticationManager (parent : parentDaoAuthenticationManager, fallback 용)
  - ROLE_ADMIN 필요
- "/api"
  - SecurityFilterChain(apiChain) 으로 동작함.
  - AuthenticationManager : jwtAuthenticationManager (parent : parentDaoAuthenticationManager, fallback 용)
  - ROLE_DEVELOPER 필요
- 그외 path
  - SecurityFilterChain(loginChain) 으로 동작함.
  - spring security 기본 formLogin 설정을 따름.
  - AuthenticationManager : parentDaoAuthenticationManager (parent : null)
  - ROLE_USER 필요

### api 스팩
- test.http 참조

### AuthenticationFilter 설명
- ApiKeyAuthenticationFilter
  - "/admin/**" 을 담당하는 SecurityFilterChain(adminChain) 의 핵심 AuthenticationFilter
  - Stateless authentication via X-API-KEY header (세션 사용 X)
- JwtAuthenticationFilter
  - "/api/**" 을 담당하는 SecurityFilterChain(apiChain) 의 핵심 AuthenticationFilter
  - Stateless authentication via Authorization header (세션 사용 X)
- UsernamePasswordAuthenticationFilter
  - 그외 path 을 담당하는 SecurityFilterChain(loginChain) 의 핵심 AuthenticationFilter
  - Session-based authentication (세션 사용 O)
