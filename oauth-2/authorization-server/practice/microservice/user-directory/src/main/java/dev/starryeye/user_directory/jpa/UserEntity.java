package dev.starryeye.user_directory.jpa;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(unique = true, nullable = false)
	private String sub; // 사용자 고유 식별자 (토큰 sub claim 이 된다)

	@Column(unique = true, nullable = false)
	private String username;

	@Column(nullable = false)
	private String password; // {bcrypt}.. 인코딩 저장

	@Column(nullable = false, length = 500)
	private String authorities; // comma 구분

	// OIDC profile scope 대응 claim
	private String name;

	private String nickname;

	private String preferredUsername;

	// OIDC email scope 대응 claim
	private String email;

	private boolean emailVerified;

	@Builder
	private UserEntity(String sub, String username, String password, String authorities,
			String name, String nickname, String preferredUsername, String email, boolean emailVerified) {
		this.sub = sub;
		this.username = username;
		this.password = password;
		this.authorities = authorities;
		this.name = name;
		this.nickname = nickname;
		this.preferredUsername = preferredUsername;
		this.email = email;
		this.emailVerified = emailVerified;
	}
}
