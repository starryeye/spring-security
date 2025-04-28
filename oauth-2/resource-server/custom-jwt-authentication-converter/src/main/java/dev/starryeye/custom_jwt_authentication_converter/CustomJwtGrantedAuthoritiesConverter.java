package dev.starryeye.custom_jwt_authentication_converter;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CustomJwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final String AUTHORITIES_PREFIX = "ROLE_";

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {

        /**
         * 기본 JwtAuthenticationConverter 를 사용하면 prefix 는 "SCOPE_" 이고 scope claim 을 권한으로 매핑시킨다.
         * 여기서는 기본 설정을 사용하지 않고 resource_access.account.roles 로 제공되는 값들을 사용해본다.
         */

        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess == null || !resourceAccess.containsKey("account")) {
            return Collections.emptyList();
        }

        Map<String, Object> account = (Map<String, Object>) resourceAccess.get("account");
        if (account == null || !account.containsKey("roles")) {
            return Collections.emptyList();
        }

        List<String> roles = (List<String>) account.get("roles");

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority(AUTHORITIES_PREFIX + role))
                .collect(Collectors.toSet());
    }
}
