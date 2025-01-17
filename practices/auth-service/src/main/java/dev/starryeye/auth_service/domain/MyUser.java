package dev.starryeye.auth_service.domain;

import dev.starryeye.auth_service.domain.type.MyRoleName;
import dev.starryeye.auth_service.jpa.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.HashSet;
import java.util.List;
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

    @Column(unique = true, nullable = false)
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

    public static MyUser create(String username, String password, Integer age) {
        return MyUser.builder()
                .id(null)
                .username(username)
                .password(password)
                .age(age)
                .roles(new HashSet<>())
                .build();
    }

    public static MyUser createWithRoles(String username, String password, Integer age, Set<MyUserRole> roles) {
        return MyUser.builder()
                .id(null)
                .username(username)
                .password(password)
                .age(age)
                .roles(roles)
                .build();
    }

    public void changePassword(String newPassword) {
        this.password = newPassword;
    }

    public void changeAge(Integer newAge) {
        this.age = newAge;
    }

    public void changeRoles(Set<MyUserRole> newRoles) {
        /**
         * orphanRemover 옵션으로 정상 동작시키려면 ORM 이 collection 을 추적할 수 있도록
         * clear -> add 로 해야한다.
         *      - 그냥 this.roles = newRoles 로 하면 에러난다.
         */
        this.roles.clear();
        this.roles.addAll(newRoles);
    }

    public void updateRolesIncremental(List<MyUserRole> newRoles) {

        /**
         * 증분 업데이트
         *      changeRoles 은 모두 삭제 후 생성이지만,
         *      updateRolesIncremental 은 이미 존재하는 role 이면 놔두고, 신규는 생성, newRoles 에 없으면 삭제
         *
         * todo, MyRole.. 값 타입 엔티티.. 고려
         */

        // 기존 role 에서 삭제 대상
        this.roles.removeIf(myUserRole ->
                !newRoles.stream()
                        .map(MyUserRole::getMyRole)
                        .map(MyRole::getName)
                        .toList()
                        .contains(myUserRole.getMyRole().getName())
        );

        // 2. 추가 대상 삽입
        for (MyRole newRole : newRoles.stream().map(MyUserRole::getMyRole).toList()) {
            // 이미 존재하는지 확인
            boolean alreadyExists = this.roles.stream()
                    .anyMatch(userRole -> userRole.getMyRole().getName().equals(newRole.getName()));

            if (!alreadyExists) {
                // 없으면 새로 추가
                MyUserRole userRole = MyUserRole.create(this, newRole);
                this.roles.add(userRole);
            }
        }
    }
}
