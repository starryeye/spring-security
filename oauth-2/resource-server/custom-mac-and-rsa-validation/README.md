### architecture
- SecurityFilterChain 에 설정한 Filter 별로 패키지를 구성하였다.
  - 특정 Filter 에서만 사용되는 Authentication, AuthenticationManager, AuthenticationProvider 등은 하위에 위치함
- Filter 간에 공통으로 사용되는 것은 FilterChainConfig 에서 다루도록 하였다.
- username, password 로 인증하는 것은 formLogin() 설정 api 를 사용하여 커스텀해도 되지만 직접 만들어보았다.
  - CustomUsernamePasswordAuthenticationFilter
- 인증 처리는 JWT 로 진행하므로 stateless 정책을 따르도록 함 (쿠키 세션 X)

### authentication filter
- CustomUsernamePasswordAuthenticationFilter
  - POST /token 요청 시 수행됨, 나머지는 pass
  - AuthenticationManager : 기본 생성되는 DaoAuthenticationProvider 를 가진 AuthenticationManager 를 사용한다.
  - AuthenticationSuccessHandler : JwtIssuerOnAuthenticationSuccess 를 사용하여 인증에 성공 시 JWT 발행 (대칭키 MAC 방식)
- JwtVerificationFilter
  - 모든 요청, Authorization 헤더 및 헤더 값 prefix 가 Bearer 일 경우 수행됨, 나머지는 pass
  - AuthenticationManager : JwtAuthenticationProvider 를 가진 ProviderManager 를 사용한다.

### api 및 기능
- api 스팩
  - test.http 참조
- "/" path 를 제외한 나머지 전체 요청 path 는 인증을 필요로 한다.
- "POST /token"
  - request body 에 username, password 로 요청하면 JWT 가 발행된다. (인증 성공 처리 이후 successHandler 에서 최종 발행)
- 