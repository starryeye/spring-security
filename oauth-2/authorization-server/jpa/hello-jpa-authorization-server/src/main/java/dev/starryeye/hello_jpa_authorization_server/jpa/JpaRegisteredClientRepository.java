package dev.starryeye.hello_jpa_authorization_server.jpa;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class JpaRegisteredClientRepository implements RegisteredClientRepository {

    /**
     * RegisteredClientRepository 를 JPA 로 직접 구현해본다.
     *
     * 구현해야 할 메서드는 3개 뿐이다.
     *      save : client 등록/수정
     *          client 사전 등록(pre-registration) 기능은 프레임워크가 제공하지 않으므로 개발자가 직접 만들어 save 를 호출해야한다. (RegisteredClientController 참고)
     *          프레임워크가 save 를 호출하는 경우도 두가지 있다.. secret 인코딩 upgrade, OIDC dynamic client registration (RegisteredClientController 주석 참고)
     *      findById : RegisteredClient.id (저장소 식별자) 로 조회
     *      findByClientId : client_id (프로토콜 상의 클라이언트 식별자) 로 조회
     *
     * 프레임워크가 이 저장소를 호출하는 대표 시점.. (registered-client-repository, custom-authorization-endpoint 프로젝트 주석 참고)
     *      OAuth2AuthorizationCodeRequestAuthenticationProvider
     *          "/oauth2/authorize" 요청 검증 시 findByClientId 로 조회한다.
     *      ClientSecretAuthenticationProvider
     *          "/oauth2/token" 등의 요청에서 client 인증 시 findByClientId 로 조회한다.
     *      -> show-sql 설정을 켜두면 grant flow 진행 중에 select 쿼리가 찍히는 것으로 호출 시점을 관찰할 수 있다.
     *
     * 참고.
     * entity <-> RegisteredClient 변환 방식은 JdbcRegisteredClientRepository 의 RegisteredClientRowMapper 를 참고했다.
     *      collection 필드는 comma 구분 문자열로 저장한다. (StringUtils 유틸 사용)
     */

    private final RegisteredClientEntityRepository repository;

    @Override
    @Transactional
    public void save(RegisteredClient registeredClient) {
        repository.save(toEntity(registeredClient));
    }

    @Override
    @Transactional(readOnly = true)
    public RegisteredClient findById(String id) {
        return repository.findById(id)
                .map(this::toObject)
                .orElse(null); // 못 찾으면 null 리턴이 인터페이스 규약이다.
    }

    @Override
    @Transactional(readOnly = true)
    public RegisteredClient findByClientId(String clientId) {
        return repository.findByClientId(clientId)
                .map(this::toObject)
                .orElse(null);
    }

    private RegisteredClientEntity toEntity(RegisteredClient registeredClient) {

        Set<String> clientAuthenticationMethods = registeredClient.getClientAuthenticationMethods().stream()
                .map(ClientAuthenticationMethod::getValue)
                .collect(Collectors.toSet());
        Set<String> authorizationGrantTypes = registeredClient.getAuthorizationGrantTypes().stream()
                .map(AuthorizationGrantType::getValue)
                .collect(Collectors.toSet());

        return RegisteredClientEntity.builder()
                .id(registeredClient.getId())
                .clientId(registeredClient.getClientId())
                .clientSecret(registeredClient.getClientSecret())
                .clientName(registeredClient.getClientName())
                .clientAuthenticationMethods(StringUtils.collectionToCommaDelimitedString(clientAuthenticationMethods))
                .authorizationGrantTypes(StringUtils.collectionToCommaDelimitedString(authorizationGrantTypes))
                .redirectUris(StringUtils.collectionToCommaDelimitedString(registeredClient.getRedirectUris()))
                .scopes(StringUtils.collectionToCommaDelimitedString(registeredClient.getScopes()))
                .requireAuthorizationConsent(registeredClient.getClientSettings().isRequireAuthorizationConsent())
                .build();
    }

    private RegisteredClient toObject(RegisteredClientEntity entity) {

        return RegisteredClient.withId(entity.getId())
                .clientId(entity.getClientId())
                .clientSecret(entity.getClientSecret())
                .clientName(entity.getClientName())
                .clientAuthenticationMethods(methods ->
                        StringUtils.commaDelimitedListToSet(entity.getClientAuthenticationMethods()).forEach(value ->
                                methods.add(new ClientAuthenticationMethod(value))
                        )
                )
                .authorizationGrantTypes(grantTypes ->
                        StringUtils.commaDelimitedListToSet(entity.getAuthorizationGrantTypes()).forEach(value ->
                                grantTypes.add(new AuthorizationGrantType(value))
                        )
                )
                .redirectUris(uris -> uris.addAll(StringUtils.commaDelimitedListToSet(entity.getRedirectUris())))
                .scopes(scopes -> scopes.addAll(StringUtils.commaDelimitedListToSet(entity.getScopes())))
                .clientSettings(
                        ClientSettings.builder()
                                .requireAuthorizationConsent(entity.isRequireAuthorizationConsent())
                                .build()
                )
                .build();
    }
}
