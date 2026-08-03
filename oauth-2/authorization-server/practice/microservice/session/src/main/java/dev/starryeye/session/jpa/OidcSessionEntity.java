package dev.starryeye.session.jpa;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Getter
@Table(name = "oidc_sessions", uniqueConstraints =
		@UniqueConstraint(name = "uk_sid_client", columnNames = {"sid", "client_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OidcSessionEntity {

	/**
	 * "OP 세션 sid 에서 client_id 가 세션을 갖고 있다" 는 사실 하나를 담는다.
	 *      한 sid 에 RP 수만큼 행이 생긴다.
	 *
	 * 주의. (sid, client_id) 가 unique 다. 같은 RP 가 여러 번 code 를 교환해도 행이 늘면 안 된다.
	 *      늘어나면 로그아웃 때 같은 RP 로 logout token 을 여러 번 보내게 된다.
	 */

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 64)
	private String sid;

	@Column(nullable = false, length = 64)
	private String sub;

	@Column(name = "client_id", nullable = false, length = 100)
	private String clientId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Builder
	private OidcSessionEntity(String sid, String sub, String clientId, Instant createdAt) {
		this.sid = sid;
		this.sub = sub;
		this.clientId = clientId;
		this.createdAt = createdAt;
	}
}
