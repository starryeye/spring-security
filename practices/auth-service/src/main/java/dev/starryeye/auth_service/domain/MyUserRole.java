package dev.starryeye.auth_service.domain;

import dev.starryeye.auth_service.jpa.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "user_role")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MyUserRole extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "user_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private MyUser myUser;

    @JoinColumn(name = "role_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private MyRole myRole;

    @Builder
    private MyUserRole(Long id, MyUser myUser, MyRole myRole) {
        this.id = id;
        this.myUser = myUser;
        this.myRole = myRole;
    }

    public static MyUserRole create(MyUser myUser, MyRole myRole) {
        return MyUserRole.builder()
                .id(null)
                .myUser(myUser)
                .myRole(myRole)
                .build();
    }

    public static List<MyUserRole> createUserRoles(MyUser myUser, List<MyRole> myRoles) {
        List<MyUserRole> myUserRoles = new ArrayList<>();
        for (MyRole myRole : myRoles) {
            myUserRoles.add(create(myUser, myRole));
        }
        return myUserRoles;
    }
}
