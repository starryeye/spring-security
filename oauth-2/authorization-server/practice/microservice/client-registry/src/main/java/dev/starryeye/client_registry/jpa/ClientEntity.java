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

	@Builder
	private ClientEntity(String clientId, String clientSecretHash, String redirectUris, String scopes, String grantTypes) {
		this.clientId = clientId;
		this.clientSecretHash = clientSecretHash;
		this.redirectUris = redirectUris;
		this.scopes = scopes;
		this.grantTypes = grantTypes;
	}
}
