package dev.starryeye.auth.security;

import dev.starryeye.auth.client.UserDirectoryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RemoteAuthenticationProvider implements org.springframework.security.authentication.AuthenticationProvider {

	/**
	 * 로그인 password 검증을 user-directory 에 위임하는 AuthenticationProvider 이다.
	 *      DaoAuthenticationProvider 와 달리 password 해시를 로컬에서 다루지 않는다.. bcrypt 비교는 user-directory 몫이다.
	 *      인증 성공 시 principal name 을 username 이 아니라 sub 로 둔다. (토큰 sub 와 일치)
	 */

	private final UserDirectoryClient userDirectoryClient;

	@Override
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		String username = authentication.getName();
		String password = authentication.getCredentials().toString();

		UserDirectoryClient.AuthenticatedUser user;
		try {
			user = userDirectoryClient.authenticate(username, password);
		} catch (UserDirectoryClient.BadCredentialsRemoteException e) {
			throw new BadCredentialsException("invalid credentials");
		}

		List<SimpleGrantedAuthority> authorities = user.authorities().stream()
				.map(SimpleGrantedAuthority::new).toList();
		return new UsernamePasswordAuthenticationToken(user.sub(), null, authorities);
	}

	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}
}
