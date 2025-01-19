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
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MyRoleHierarchy extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @JoinColumn(name = "parent_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private MyRoleHierarchy parent;

    @OneToMany(mappedBy = "parent")
    private Set<MyRoleHierarchy> children = new HashSet<>();

    @Builder
    private MyRoleHierarchy(Long id, String name, MyRoleHierarchy parent, Set<MyRoleHierarchy> children) {
        this.id = id;
        this.name = name;
        this.parent = parent;
        this.children = children;
    }

    public static MyRoleHierarchy create(String name, MyRoleHierarchy parent) {
        return MyRoleHierarchy.builder()
                .id(null)
                .name(name)
                .parent(parent)
                .build();
    }
}
