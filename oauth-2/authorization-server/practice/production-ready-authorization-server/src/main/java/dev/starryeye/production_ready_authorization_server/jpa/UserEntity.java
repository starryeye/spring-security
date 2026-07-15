package dev.starryeye.production_ready_authorization_server.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity {

    /**
     * 로그인 사용자를 저장하기 위한 엔티티이다.
     *      spring security 는 사용자 스키마를 강제하지 않는다.. UserDetailsService 구현(JpaUserDetailsService)이 알아서 조회하면 된다.
     *      참고. JdbcUserDetailsManager 용 공식 스키마(users/authorities 두 테이블)도 있지만 여기서는 comma 구분 단일 테이블로 단순화했다.
     */

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password; // 인코딩된 값으로 저장 ({bcrypt}..)

    @Column(nullable = false, length = 500)
    private String authorities; // comma 구분 문자열 (예. "ROLE_USER,ROLE_CUSTOMER")

    @Column(nullable = false)
    private boolean enabled; // false 면 DaoAuthenticationProvider 가 DisabledException 으로 거부한다. (계정 정지 처리 확장 지점)

    @Builder
    private UserEntity(String username, String password, String authorities, boolean enabled) {
        this.username = username;
        this.password = password;
        this.authorities = authorities;
        this.enabled = enabled;
    }
}
