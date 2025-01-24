# 실습 프로젝트

## projects
- auth-service
  - base security
    - formLogin 기반
    - home
      - localhost:8080/
  - ajax_api security
    - ajax 기반
    - home
      - localhost:8080/api
  - user, role, resource management
    - formLogin 기반 security 에 설정
    - home
      - localhost:8080/admin
     
## TODO
- auth-service 에서 admin 분리
- 분산 시스템을 위한 redis 추가
  - session
  - 자원-권한 정보(실시간 처리)
- 계층적 권한 적용해보기
  - admin 페이지 및 관련 api 만들기 
  - MyRoleHierarchy 와 MyRole 연관관계 맺어서해보기
  - 실시간 처리 필요?
