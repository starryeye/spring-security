package dev.starryeye.user_directory;

import dev.starryeye.user_directory.dto.*;
import dev.starryeye.user_directory.jpa.UserEntity;
import dev.starryeye.user_directory.jpa.UserEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class UserController {

	/**
	 * 사용자 조회와 credential 검증 API 이다. (계약은 공유 REST 계약 참고)
	 *      authenticate 는 성공 시 sub/authorities 만 돌려준다. password 해시는 응답에 절대 넣지 않는다.
	 */

	private final UserEntityRepository repository;
	private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

	@PostMapping("/internal/users/authenticate")
	public AuthenticateResponse authenticate(@RequestBody AuthenticateRequest request) {
		UserEntity user = repository.findByUsername(request.username())
				.filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid credentials"));
		return new AuthenticateResponse(user.getSub(), toList(user.getAuthorities()));
	}

	@GetMapping("/internal/users/{sub}")
	public UserResponse getUser(@PathVariable String sub) {
		UserEntity user = repository.findBySub(sub)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
		return new UserResponse(
				user.getSub(), user.getUsername(), toList(user.getAuthorities()),
				user.getName(), user.getNickname(), user.getPreferredUsername(),
				user.getEmail(), user.isEmailVerified());
	}

	private List<String> toList(String commaDelimited) {
		return List.of(StringUtils.commaDelimitedListToStringArray(commaDelimited));
	}
}
