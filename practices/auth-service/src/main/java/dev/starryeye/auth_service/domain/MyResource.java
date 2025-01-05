package dev.starryeye.auth_service.domain;

import dev.starryeye.auth_service.domain.type.MyResourceType;
import dev.starryeye.auth_service.jpa.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.util.Set;

@Getter
@Entity
@Table(name = "resource")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MyResource extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Enumerated(EnumType.STRING)
    private MyResourceType type;

    private String httpMethod;

    private String orderNumber;

    @OneToMany(mappedBy = "myResource", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MyRoleResource> roles;

    @Builder
    private MyResource(Long id, String name, MyResourceType type, String httpMethod, String orderNumber, Set<MyRoleResource> roles) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.httpMethod = httpMethod;
        this.orderNumber = orderNumber;
        this.roles = roles;
    }

    public static MyResource create(String name, MyResourceType type, String httpMethod, String orderNumber, Set<MyRoleResource> roles) {
        return MyResource.builder()
                .id(null)
                .name(name)
                .type(type)
                .httpMethod(httpMethod)
                .orderNumber(orderNumber)
                .roles(roles)
                .build();
    }
}
