package dev.starryeye.auth_service.domain;

import dev.starryeye.auth_service.jpa.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MyUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String password;

    private String username;
    private Integer age;

    private String roles;

    @Builder
    private MyUser(Long id, String username, String password, Integer age, String roles) {
        this.id = id;
        this.password = password;
        this.username = username;
        this.age = age;
        this.roles = roles;
    }

    public static MyUser create(String username, String password, Integer age, String roles) {
        return MyUser.builder()
                .id(null)
                .username(username)
                .password(password)
                .age(age)
                .roles(roles)
                .build();
    }
}
