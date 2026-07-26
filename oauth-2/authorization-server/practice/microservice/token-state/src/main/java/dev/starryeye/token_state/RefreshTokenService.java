package dev.starryeye.token_state;

import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

	/**
	 * refresh token 의 발급 · 회전 · 폐기 · 조회를 담당한다. 상태 전이 판정이 전부 여기 모여 있고,
	 *      컨트롤러는 위임만 한다.
	 *
	 * 주의. scope 는 API 경계에서 공백 구분(OAuth 와이어 포맷), DB 에서 comma 구분이다. 변환은 이 클래스에서만 한다.
	 */

	private final RefreshTokenEntityRepository repository;
	private final TokenGenerator tokenGenerator;
	private final long ttlSeconds;
	private final long familyMaxSeconds;

	public RefreshTokenService(
			RefreshTokenEntityRepository repository,
			TokenGenerator tokenGenerator,
			@Value("${my.refresh-token-ttl-seconds}") long ttlSeconds,
			@Value("${my.refresh-family-max-seconds}") long familyMaxSeconds
	) {
		this.repository = repository;
		this.tokenGenerator = tokenGenerator;
		this.ttlSeconds = ttlSeconds;
		this.familyMaxSeconds = familyMaxSeconds;
	}

	@Transactional
	public IssueResult issue(String clientId, String sub, String scope, long authTime) {
		Instant now = Instant.now();
		String familyId = UUID.randomUUID().toString();
		String token = tokenGenerator.generate();

		RefreshTokenEntity entity = RefreshTokenEntity.builder()
				.tokenHash(tokenGenerator.hash(token))
				.familyId(familyId)
				.clientId(clientId)
				.sub(sub)
				.scopes(toCommaDelimited(scope))
				.authTime(authTime)
				.issuedAt(now)
				.expiresAt(now.plusSeconds(ttlSeconds))
				.familyExpiresAt(now.plusSeconds(familyMaxSeconds))
				.build();
		repository.save(entity);

		return new IssueResult(token, entity.getExpiresAt().getEpochSecond(), familyId);
	}

	/**
	 * 회전을 한 트랜잭션 안에서 끝낸다. 조회 · 판정 · 전이를 호출자에게 쪼개 주면 왕복 사이에 경쟁 창이 생겨
	 *      재사용 탐지가 무력해지므로, 연산 하나를 한 번의 호출로 표현한다.
	 *
	 * 주의. 잠금은 반드시 "계열 전체 → 그 안의 대상 행" 한 가지 순서로만 얻는다. familyId 를 알아내는
	 *      첫 조회는 잠금 없이 하고(findByTokenHash), 그다음 findByFamilyIdForUpdate 로 계열의 모든 행을
	 *      한 번에 잠근 뒤, 대상 토큰 행을 그 잠긴 결과 안에서 다시 찾아 판정한다. 모든 호출이 이 순서
	 *      하나만 쓰므로, 어떤 경로도 "행 먼저 → 계열"을 타지 않는다 — 대상 행만 먼저 잠그고 그다음
	 *      계열을 잠그는 경로가 하나라도 남아 있으면, 계열을 통째로 먼저 잠그는 다른 트랜잭션과 서로
	 *      상대가 쥔 잠금을 기다리는 교착이 가능해진다.
	 *
	 * 주의. 판정은 첫 조회(잠금 없음) 결과가 아니라 잠근 뒤 findByFamilyIdForUpdate 로 다시 읽은 상태로
	 *      한다. 첫 조회와 잠금 획득 사이에 다른 트랜잭션이 이 계열에 회전 · 폐기를 커밋할 수 있어,
	 *      오래된 스냅샷으로 판정하면 이미 소진된 토큰이나 이미 폐기된 계열을 놓친다.
	 *
	 * 주의. 이미 소진된(CONSUMED) 토큰이 다시 오면 계열 전체를 폐기한다. 정상 사용자와 공격자 중 누가 먼저
	 *      회전했든 다른 쪽이 CONSUMED 를 만나므로, 양쪽을 모두 재인증으로 떨어뜨려 조용한 지속 접근을 끊는다.
	 *      정상 client 의 단순 재시도까지 계열을 죽이는 것은 회전의 알려진 대가다. 폐기 대상은 방금
	 *      findByFamilyIdForUpdate 로 잠근 목록 그대로이므로, 동시에 삽입된 형제 행도 놓치지 않는다.
	 */
	@Transactional
	public RotateResult rotate(String refreshToken, String clientId) {

		Instant now = Instant.now();
		String tokenHash = tokenGenerator.hash(refreshToken);

		Optional<RefreshTokenEntity> unlocked = repository.findByTokenHash(tokenHash);
		if (unlocked.isEmpty()) {
			return RotateResult.failed(RotateStatus.NOT_FOUND);
		}

		List<RefreshTokenEntity> family = repository.findByFamilyIdForUpdate(unlocked.get().getFamilyId());
		Optional<RefreshTokenEntity> found = family.stream()
				.filter(member -> member.getTokenHash().equals(tokenHash))
				.findFirst();
		if (found.isEmpty()) {
			return RotateResult.failed(RotateStatus.NOT_FOUND);
		}
		RefreshTokenEntity entity = found.get();

		if (!entity.getClientId().equals(clientId)) {
			return RotateResult.failed(RotateStatus.CLIENT_MISMATCH);
		}
		if (entity.getStatus() == RefreshTokenStatus.REVOKED) {
			return RotateResult.failed(RotateStatus.REVOKED);
		}
		if (entity.getStatus() == RefreshTokenStatus.CONSUMED) {
			revokeFamily(family, now, "REUSE_DETECTED");
			return RotateResult.failed(RotateStatus.REUSE_DETECTED);
		}
		if (isExpired(entity, now)) {
			return RotateResult.failed(RotateStatus.EXPIRED);
		}

		entity.consume(now);

		String token = tokenGenerator.generate();
		RefreshTokenEntity rotated = RefreshTokenEntity.builder()
				.tokenHash(tokenGenerator.hash(token))
				.familyId(entity.getFamilyId())
				.clientId(entity.getClientId())
				.sub(entity.getSub())
				.scopes(entity.getScopes()) // 축소 요청이 있어도 저장 scope 는 그대로다
				.authTime(entity.getAuthTime())
				.issuedAt(now)
				.expiresAt(now.plusSeconds(ttlSeconds))
				.familyExpiresAt(entity.getFamilyExpiresAt()) // 절대 상한은 복사만, 연장하지 않는다
				.build();
		repository.save(rotated);

		return new RotateResult(
				RotateStatus.ROTATED,
				entity.getSub(),
				toSpaceDelimited(entity.getScopes()),
				entity.getAuthTime(),
				token,
				rotated.getExpiresAt().getEpochSecond()
		);
	}

	/**
	 * 계열 전체를 폐기한다. 요청 client 의 토큰이 아니면 아무것도 하지 않는다.
	 *
	 * 주의. 잠금 순서는 rotate 와 동일하게 "계열 전체 → 그 안의 대상 행" 하나만 쓴다. familyId 를 알아내는
	 *      첫 조회는 잠금 없이 하고(findByTokenHash), findByFamilyIdForUpdate 로 계열의 모든 행을 한 번에
	 *      잠근 뒤, 대상 토큰 행을 그 잠긴 결과 안에서 다시 찾아 client 를 판정한다. 대상 행만 먼저 잠그고
	 *      그다음 계열을 잠그는 경로를 쓰면, 계열을 먼저 잠그는 rotate 와 반대 순서로 잠그게 돼 교착이
	 *      가능해지므로 쓰지 않는다.
	 */
	@Transactional
	public boolean revoke(String refreshToken, String clientId) {
		String tokenHash = tokenGenerator.hash(refreshToken);

		Optional<RefreshTokenEntity> unlocked = repository.findByTokenHash(tokenHash);
		if (unlocked.isEmpty()) {
			return false;
		}

		List<RefreshTokenEntity> family = repository.findByFamilyIdForUpdate(unlocked.get().getFamilyId());
		Optional<RefreshTokenEntity> found = family.stream()
				.filter(member -> member.getTokenHash().equals(tokenHash))
				.findFirst();
		if (found.isEmpty()) {
			return false;
		}
		RefreshTokenEntity entity = found.get();

		if (!entity.getClientId().equals(clientId)) {
			return false;
		}

		revokeFamily(family, Instant.now(), "CLIENT_REVOKED");
		return true;
	}

	private void revokeFamily(List<RefreshTokenEntity> family, Instant at, String reason) {
		for (RefreshTokenEntity member : family) {
			member.revoke(at, reason);
		}
		repository.saveAll(family);
	}

	@Transactional(readOnly = true)
	public IntrospectResult introspect(String refreshToken) {
		Optional<RefreshTokenEntity> found = repository.findByTokenHash(tokenGenerator.hash(refreshToken));
		if (found.isEmpty()) {
			return IntrospectResult.inactive();
		}
		RefreshTokenEntity entity = found.get();
		if (entity.getStatus() != RefreshTokenStatus.ACTIVE || isExpired(entity, Instant.now())) {
			return IntrospectResult.inactive();
		}
		return new IntrospectResult(
				true,
				entity.getSub(),
				entity.getClientId(),
				toSpaceDelimited(entity.getScopes()),
				entity.getExpiresAt().getEpochSecond(),
				entity.getIssuedAt().getEpochSecond()
		);
	}

	private boolean isExpired(RefreshTokenEntity entity, Instant now) {
		return entity.getExpiresAt().isBefore(now) || entity.getFamilyExpiresAt().isBefore(now);
	}

	private String toCommaDelimited(String spaceDelimited) {
		return String.join(",", spaceDelimited.trim().split("\\s+"));
	}

	private String toSpaceDelimited(String commaDelimited) {
		return String.join(" ", commaDelimited.split(","));
	}
}
