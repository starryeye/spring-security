package dev.starryeye.auth_service.domain;

import dev.starryeye.auth_service.jpa.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.HashSet;
import java.util.Set;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MyUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String password;

    private Integer age;

    @OneToMany(mappedBy = "myUser", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MyUserRole> roles = new HashSet<>();

    @Builder
    private MyUser(Long id, String username, String password, Integer age, Set<MyUserRole> roles) {
        this.id = id;
        this.password = password;
        this.username = username;
        this.age = age;
        this.roles = roles;
    }

    public static MyUser create(String username, String password, Integer age, Set<MyUserRole> roles) {
        return MyUser.builder()
                .id(null)
                .username(username)
                .password(password)
                .age(age)
                .roles(roles)
                .build();
    }
}
