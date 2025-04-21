## custom-mac-jwt-issuer-verifier
- resource-server 에서 토큰을 검증하는 시나리오를 알아보기 전에 2가지 방식으로 직접 구현해본다.
- JWT 를 발행하는 것은 resource-server 역할이 아니지만, 편의를 위해 구현해봄.
- 검증 2가지 방식
  - 1. 직접 security authentication filter 를 구현 (mac_jwt_1)
  - 2. oauth2ResourceServer() 설정 api, JwtDecoder 등록해보는 방식

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
- mac_jwt_1 방식
  - FilterChainConfig 에서 .addFilterBefore(jwtVerifierFilter, UsernamePasswordAuthenticationFilter.class)를 활성화 해야함
  - 직접 커스텀 Filter 사용, spring-resource-server 의존성 X
    - JwtVerificationFilter
      - 모든 요청, Authorization 헤더 및 헤더 값 prefix 가 Bearer 일 경우 수행됨, 나머지는 pass
      - AuthenticationManager : JwtAuthenticationProvider 를 가진 ProviderManager 를 사용한다.
- mac_jwt_2 방식
  - FilterChainConfig 에서 oauth2ResourceServer() 설정 활성화 해야함.
  - oauth2ResourceServer() 설정 api 사용, BearTokenAuthenticationFilter 를 사용하게됨
  - mac_jwt_1 과 거의 같은 비즈니스 로직을 가진다.
  - mac_jwt_1 에서는 직접 토큰을 검증하고 직접 인증객체도 만들고 했지만, mac_jwt_2 에서는 oauth2-resource-server 에서 기본적으로 제공하는 객체들로 대체함
    - 토큰 검증 시, 검증 방식은 직접 설정해햐하는데 JwtDecoder 를 직접 등록해주고 있음 (JwtDecoderConfig 참조)

### api 및 기능
- api 스팩
  - test.http 참조
- "/" path 를 제외한 나머지 전체 요청 path 는 인증을 필요로 한다.
- "POST /token"
  - request body 에 username, password 로 요청하면 JWT 가 발행된다. (인증 성공 처리 이후 successHandler 에서 최종 발행)