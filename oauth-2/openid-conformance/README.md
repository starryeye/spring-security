# OpenID Conformance Suite 테스트 가이드

- OpenID Foundation(OIDF)이 만드는 공식 적합성 테스트 도구로 OAuth 2.0 / OpenID Connect 구현이 스펙대로 동작하는지 검증한다.
  - 오픈소스: https://gitlab.com/openid/conformance-suite (테스트 사용은 무료, "OpenID Certified" 인증 등록만 유료)
  - 호스팅 버전: https://www.certification.openid.net (Google/GitLab 계정으로 로그인)
- keycloak 등의 "OpenID Certified" 마크가 이 suite 통과 + OIDF 등록의 결과물이다.

## 테스트의 두 방향

suite 는 내 구현의 "반대편 역할" 을 연기하며 정상/비정상 케이스를 던진다.

| 내가 테스트할 것 | suite 의 역할 | 가이드 |
|---|---|---|
| authorization server (OP) | RP(client)가 되어 OP 를 공략 | [authorization-server/](authorization-server/README.md) |
| client (RP) | 가짜 OP 가 되어 RP 의 검증 로직을 공략 | [client/](client/README.md) |

## 로컬 vs 인터넷 공개, 어느 방식을 쓰나

방향에 따라 "누가 누구에게 접근해야 하는가" 가 달라서 선택 기준이 다르다.

- **OP 테스트**: suite 가 내 서버의 token endpoint 등을 직접 호출한다(back-channel).
  - 서버가 인터넷에 공개되어 있다면 -> 호스팅 suite (설치 없음, 가장 간단)
  - 로컬/사내 서버라면 -> suite 를 docker 로 self-host (서버 옆에 띄운다)
- **RP 테스트**: 내 client 가 suite 로 나가는(outbound) 요청만 하면 된다.
  - **로컬 client 도 호스팅 suite 로 바로 테스트할 수 있다** (인터넷 공개 불필요.. 가장 간단, 권장)
  - 폐쇄망이거나 CI 에 넣을 때 -> self-host

## 실무에서 누가 쓰나

- **OP/IdP 를 만드는 쪽의 표준 관행이다..** keycloak, authlete, curity 같은 IdP 제품 벤더가 "OpenID Certified" 인증을 위해 사용하고,
  자체 인증 서버를 만드는 조직이 스펙 검증 도구로 사용한다.
- **금융/오픈뱅킹 생태계에서는 의무다..** 브라질 Open Finance, 영국 오픈뱅킹, 호주 CDR 등은 FAPI 프로파일의
  conformance 인증이 참여 요건이라, 참여 기관 개발자들이 suite 를 CI 파이프라인에 넣고 상시로 돌린다.
  (suite 레포의 scripts/run-test-plan.py 가 그 용도로 제공된다)
- **일반 앱 개발자의 일상 도구는 아니다..** 소셜 로그인을 붙이는 수준의 client 개발은 라이브러리(spring-security 등)가
  스펙 준수를 책임지므로, RP 인증은 주로 라이브러리/SDK 제작자가 받는다. 일반 앱은 spring-security-test 와 통합 테스트 수준이면 충분하다.

## 참고

- 이 repo 의 실전 기록: [practice/production-ready-authorization-server/openid-conformance/](../authorization-server/practice/production-ready-authorization-server/openid-conformance/README.md)
  - LB 뒤 2 인스턴스 authorization server 에 OP 테스트를 돌려 부적합 3종을 발견/수정한 과정
