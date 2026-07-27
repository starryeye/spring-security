package dev.starryeye.client_registry.jpa;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "clients")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientEntity {

	/**
	 * client 한 개를 나타낸다.
	 *
	 * 주의. scopes 와 client_scopes 는 성격이 다르다. scopes 는 사용자가 동의 화면에서 위임하는 것이고,
	 *      client_scopes 는 관리자가 client 에게 부여한 능력이라 동의 화면에 뜨지 않는다.
	 *      grant 별로 보는 컬럼이 갈린다 — authorization_code 는 scopes, client_credentials 는 client_scopes.
	 */

	@Id
	private String clientId;

	@Column(nullable = false)
	private String clientSecretHash; // {bcrypt}..

	@Column(nullable = false, length = 1000)
	private String redirectUris; // comma 구분

	@Column(nullable = false, length = 500)
	private String scopes; // comma 구분

	@Column(nullable = false, length = 500)
	private String grantTypes; // comma 구분

	@Column(name = "client_scopes", nullable = false, length = 500)
	private String clientScopes; // comma 구분. 관리자가 client 에게 부여한 능력(사용자 위임 아님)

	@Builder
	private ClientEntity(String clientId, String clientSecretHash, String redirectUris, String scopes,
			String grantTypes, String clientScopes) {
		this.clientId = clientId;
		this.clientSecretHash = clientSecretHash;
		this.redirectUris = redirectUris;
		this.scopes = scopes;
		this.grantTypes = grantTypes;
		this.clientScopes = clientScopes;
	}
}
