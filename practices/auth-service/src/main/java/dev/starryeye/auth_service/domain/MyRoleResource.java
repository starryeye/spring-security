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
@Table(name = "role_resource")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class MyRoleResource extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JoinColumn(name = "role_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private MyRole myRole;

    @JoinColumn(name = "resource_id")
    @ManyToOne(fetch = FetchType.LAZY)
    private MyResource myResource;

    @Builder
    private MyRoleResource(Long id, MyRole myRole, MyResource myResource) {
        this.id = id;
        this.myRole = myRole;
        this.myResource = myResource;
    }

    public static MyRoleResource create(MyRole myRole, MyResource myResource) {
        return MyRoleResource.builder()
                .id(null)
                .myRole(myRole)
                .myResource(myResource)
                .build();
    }
}
