package dev.starryeye.custom_social_login_client_with_form_login.config;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;

import java.util.Collection;
import java.util.HashSet;

public class CustomGrantedAuthoritiesMapper implements GrantedAuthoritiesMapper {

    /**
     * Bean 으로 등록하면, 개발자가 등록한 GrantedAuthoritiesMapper 로 권한 매핑이 된다.
     */


    private static final String DEFAULT_AUTHORITY_PREFIX = "ROLE_";
    private static final String FORMATTED_DEFAULT_AUTHORITY_PREFIX = "ROLE_%s";

    private static final String FORMATTED_ROLE_PREFIX = "SCOPE_%s";


    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {

        HashSet<GrantedAuthority> mapped = HashSet.newHashSet(authorities.size());

        for(GrantedAuthority authority : authorities) {
            mapped.add(this.mapAuthority(authority.getAuthority()));
        }

        return mapped;
    }

    private GrantedAuthority mapAuthority(String name) {

        String roleName = getParsedRoleNameForGoogle(name); // todo google 분리

        String authorityName = addDefaultAuthorityPrefix(roleName);

        return new SimpleGrantedAuthority(authorityName);
    }

    private String addDefaultAuthorityPrefix(String roleName) {
        if (!roleName.startsWith(DEFAULT_AUTHORITY_PREFIX)) {
            return FORMATTED_DEFAULT_AUTHORITY_PREFIX.formatted(roleName);
        }
        return roleName;
    }

    private String getParsedRoleNameForGoogle(String name) {
        if (name.lastIndexOf(".") != -1) { // 가장 뒤에 존재하는 "." 의 인덱스 반환, 발견하지 못하면 -1 반환
            int lastDotIndex = name.lastIndexOf(".");
            String parsingResult = name.substring(lastDotIndex + 1, name.length());
            return FORMATTED_ROLE_PREFIX.formatted(parsingResult);
        }
        return name;
    }
}
