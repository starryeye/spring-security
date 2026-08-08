-- 서비스마다 전용 스키마와 전용 계정을 만든다.
--
-- 주의. 각 계정에는 자기 스키마에만 GRANT 를 준다. 이것이 이 슬라이스의 요점이다 — 소유권을
--      "남의 테이블을 읽지 말자"는 규율이 아니라 "읽을 수 없다"는 구조로 바꾼다. 코드가 실수로
--      남의 테이블을 참조하면 컴파일은 되어도 런타임에 권한 오류로 막힌다.
--
-- 주의. root 는 지우지 않는다. 이 스크립트 자체가 root 로 실행되고, InnoDB 잠금 의미론 검증용
--      테스트(RefreshTokenServiceMySqlLockSemanticsTest)가 token_state_test 스키마를 root 로 쓴다.
--      애플리케이션 설정에서는 root 가 사라진다.

CREATE DATABASE IF NOT EXISTS ms_user_directory  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ms_client_registry CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ms_consent         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ms_token_state     CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS ms_session         CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 잠금 의미론 검증 전용. 운영 스키마와 격리된 채로 유지한다.
CREATE DATABASE IF NOT EXISTS token_state_test   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'svc_user_directory'@'%'  IDENTIFIED BY 'pw_user_directory';
CREATE USER IF NOT EXISTS 'svc_client_registry'@'%' IDENTIFIED BY 'pw_client_registry';
CREATE USER IF NOT EXISTS 'svc_consent'@'%'         IDENTIFIED BY 'pw_consent';
CREATE USER IF NOT EXISTS 'svc_token_state'@'%'     IDENTIFIED BY 'pw_token_state';
CREATE USER IF NOT EXISTS 'svc_session'@'%'         IDENTIFIED BY 'pw_session';

GRANT ALL PRIVILEGES ON ms_user_directory.*  TO 'svc_user_directory'@'%';
GRANT ALL PRIVILEGES ON ms_client_registry.* TO 'svc_client_registry'@'%';
GRANT ALL PRIVILEGES ON ms_consent.*         TO 'svc_consent'@'%';
GRANT ALL PRIVILEGES ON ms_token_state.*     TO 'svc_token_state'@'%';
GRANT ALL PRIVILEGES ON ms_session.*         TO 'svc_session'@'%';

FLUSH PRIVILEGES;
