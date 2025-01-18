package dev.starryeye.auth_service.domain;

import dev.starryeye.auth_service.domain.type.MyResourceType;
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

    private Integer orderNumber;

    @OneToMany(mappedBy = "myResource", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<MyRoleResource> roles;

    @Builder
    private MyResource(Long id, String name, MyResourceType type, String httpMethod, Integer orderNumber, Set<MyRoleResource> roles) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.httpMethod = httpMethod;
        this.orderNumber = orderNumber;
        this.roles = roles;
    }

    public static MyResource create(String name, MyResourceType type, String httpMethod, Integer orderNumber) {
        return MyResource.builder()
                .id(null)
                .name(name)
                .type(type)
                .httpMethod(httpMethod)
                .orderNumber(orderNumber)
                .roles(new HashSet<>())
                .build();
    }

    public void changeName(String newName) {
        this.name = newName;
    }

    public void changeType(MyResourceType newType) {
        this.type = newType;
    }

    public void changeHttpMethod(String newHttpMethod) {
        this.httpMethod = newHttpMethod;
    }

    public void changeOrderNumber(Integer newOrderNumber) {
        this.orderNumber = newOrderNumber;
    }
}
