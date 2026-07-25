package dev.starryeye.consent.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "consents", uniqueConstraints = @UniqueConstraint(columnNames = {"sub", "client_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConsentEntity {

	/**
	 * 동의 기록이다. (sub, clientId) 한 쌍당 한 행이며 승인된 scope 를 comma 로 보관한다.
	 *      scope 는 추가 동의 때 합집합으로 병합되므로 행이 늘지 않고 갱신된다.
	 */

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "sub", nullable = false)
	private String sub;

	@Column(name = "client_id", nullable = false)
	private String clientId;

	@Column(nullable = false, length = 1000)
	private String scopes; // comma 구분

	@Builder
	private ConsentEntity(String sub, String clientId, String scopes) {
		this.sub = sub;
		this.clientId = clientId;
		this.scopes = scopes;
	}

	public void replaceScopes(String scopes) {
		this.scopes = scopes;
	}
}
