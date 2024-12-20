package dev.starryeye.auth_service.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;

public class MyUserDetails implements UserDetails {

    private final String username;
    private final String password;
    private final Set<GrantedAuthority> authorities;

    private final MyPrincipal principal; // 원래는 UserDetails 구현체들에는 Principal 필드가 없는듯

    public MyUserDetails(String username, String password, Collection<? extends GrantedAuthority> authorities, MyPrincipal principal) {
        this.username = username;
        this.password = password;
        this.authorities = Set.copyOf(authorities);
        this.principal = principal;
    }

    public MyPrincipal getPrincipal() {
        return principal;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
