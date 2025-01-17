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
@Table(name = "role")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MyRole extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String name;

    private String description;

    private Boolean isExpression;

    @OneToMany(mappedBy = "myRole", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MyUserRole> users = new HashSet<>();

    @OneToMany(mappedBy = "myRole", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MyRoleResource> resources = new HashSet<>();

    @Builder
    private MyRole(Long id, String name, String description, Boolean isExpression, Set<MyUserRole> users, Set<MyRoleResource> resources) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.isExpression = isExpression;
        this.users = users;
        this.resources = resources;
    }

    public static MyRole create(String name, String description, Boolean isExpression) {
        return MyRole.builder()
                .id(null)
                .name(name)
                .description(description)
                .isExpression(isExpression)
                .users(new HashSet<>())
                .resources(new HashSet<>())
                .build();
    }
}
