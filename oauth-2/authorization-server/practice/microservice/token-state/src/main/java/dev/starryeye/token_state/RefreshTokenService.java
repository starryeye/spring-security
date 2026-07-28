package dev.starryeye.token_state;

import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;
import dev.starryeye.token_state.jpa.RefreshTokenStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
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
	 *      requestedScope(선택)까지 여기서 검증하는 것도 같은 이유다. 상태를 바꾸고 나서 거절하면 호출자가
	 *      되돌릴 수 없다 — 새 토큰 원문은 이 응답에서만 나오므로 버려진 회전은 영원히 회수되지 않는다.
	 *
	 * 주의. 잠금은 반드시 "계열 전체 → 그 안의 대상 행" 한 가지 순서로만 얻는다. familyId 를 알아내는
	 *      첫 조회는 잠금 없이 하고(findFamilyIdByTokenHash), 그다음 findByFamilyIdForUpdate 로 계열의 모든 행을
	 *      한 번에 잠근 뒤, 대상 토큰 행을 그 잠긴 결과 안에서 다시 찾아 판정한다. 모든 호출이 이 순서
	 *      하나만 쓰므로, 어떤 경로도 "행 먼저 → 계열"을 타지 않는다 — 대상 행만 먼저 잠그고 그다음
	 *      계열을 잠그는 경로가 하나라도 남아 있으면, 계열을 통째로 먼저 잠그는 다른 트랜잭션과 서로
	 *      상대가 쥔 잠금을 기다리는 교착이 가능해진다.
	 *
	 * 주의. 첫 조회는 엔티티를 로드하지 않는 스칼라 조회(findFamilyIdByTokenHash)여야 한다. "행 잠금을 걸었다"와
	 *      "잠긴 상태로 판정한다"는 JPA 에서 같은 말이 아니다. findByTokenHash 로 엔티티를 먼저 로드하면 그
	 *      인스턴스가 영속성 컨텍스트에 managed 로 남고, 뒤이은 findByFamilyIdForUpdate 가 SQL 로는 FOR UPDATE
	 *      잠금을 실제로 걸고 최신 행을 읽어와도 Hibernate 는 이미 있는 인스턴스를 그대로 돌려주며 필드를 갱신하지
	 *      않는다. 그러면 잠금 획득 전 스냅샷으로 판정하게 되어, 동시 회전에서 두 요청이 모두 ACTIVE 를 보고
	 *      같은 계열에 유효한 토큰을 둘 발급한다(재사용 탐지가 통째로 무력해진다).
	 *
	 * 주의. 판정은 첫 조회 결과가 아니라 잠근 뒤 findByFamilyIdForUpdate 로 다시 읽은 상태로 한다. 첫 조회와
	 *      잠금 획득 사이에 다른 트랜잭션이 이 계열에 회전 · 폐기를 커밋할 수 있어, 오래된 스냅샷으로 판정하면
	 *      이미 소진된 토큰이나 이미 폐기된 계열을 놓친다.
	 *
	 * 주의. 이미 소진된(CONSUMED) 토큰이 다시 오면 계열 전체를 폐기한다. 정상 사용자와 공격자 중 누가 먼저
	 *      회전했든 다른 쪽이 CONSUMED 를 만나므로, 양쪽을 모두 재인증으로 떨어뜨려 조용한 지속 접근을 끊는다.
	 *      정상 client 의 단순 재시도까지 계열을 죽이는 것은 회전의 알려진 대가다. 폐기 대상은 방금
	 *      findByFamilyIdForUpdate 로 잠근 목록 그대로다. h2 에서는 대기 중 다른 트랜잭션이 새로 삽입한
	 *      형제 행이 이 조회에서 빠져 ACTIVE 로 남는 경우가 관찰됐지만, 이는 h2 가 InnoDB 의 next-key/gap
	 *      lock 의미론을 재현하지 않는 테스트 DB 의 한계다. 운영 DB(MySQL 8/InnoDB)로
	 *      RefreshTokenServiceMySqlLockSemanticsTest 를 반복 실행해 확인한 바로는, 잠금을 기다리던
	 *      트랜잭션이 깨어난 뒤의 findByFamilyIdForUpdate 재조회가 그 형제 행을 항상 포함해 계열과 함께
	 *      폐기했다(design 문서 §8 참고).
	 *
	 * 주의. requestedScope 검사는 상태 전이 직전, 다른 모든 거절 사유 뒤에 둔다. 재사용 · 폐기 · 만료가 먼저
	 *      판정돼야 잘못된 scope 를 함께 보내는 것으로 재사용 탐지를 건너뛸 수 없다. 검사에 걸리면 SCOPE_EXCEEDED
	 *      를 돌려주고 아무 상태도 바꾸지 않으므로, 호출자는 원래 토큰을 그대로 다시 쓸 수 있다.
	 *
	 * 주의. 축소 요청이 통과해도 저장 scope 는 바꾸지 않는다. 회전으로 만드는 새 행은 원래 scope 를 그대로
	 *      복사하고, 축소는 호출자가 이번 access token 에만 적용한다. 아니면 한 번의 축소가 영구화된다.
	 */
	@Transactional
	public RotateResult rotate(String refreshToken, String clientId, String requestedScope) {

		Instant now = Instant.now();
		String tokenHash = tokenGenerator.hash(refreshToken);

		Optional<String> familyId = repository.findFamilyIdByTokenHash(tokenHash);
		if (familyId.isEmpty()) {
			return RotateResult.failed(RotateStatus.NOT_FOUND);
		}

		List<RefreshTokenEntity> family = repository.findByFamilyIdForUpdate(familyId.get());
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
		if (exceedsGrant(requestedScope, entity.getScopes())) {
			return RotateResult.failed(RotateStatus.SCOPE_EXCEEDED);
		}

		entity.consume(now);

		Instant rotatedExpiresAt = now.plusSeconds(ttlSeconds);
		if (rotatedExpiresAt.isAfter(entity.getFamilyExpiresAt())) {
			rotatedExpiresAt = entity.getFamilyExpiresAt(); // 계열 상한을 넘는 수명을 알리지 않는다
		}

		String token = tokenGenerator.generate();
		RefreshTokenEntity rotated = RefreshTokenEntity.builder()
				.tokenHash(tokenGenerator.hash(token))
				.familyId(entity.getFamilyId())
				.clientId(entity.getClientId())
				.sub(entity.getSub())
				.scopes(entity.getScopes()) // 축소 요청이 있어도 저장 scope 는 그대로다
				.authTime(entity.getAuthTime())
				.issuedAt(now)
				.expiresAt(rotatedExpiresAt)
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
	 *      첫 조회는 잠금 없이 하고(findFamilyIdByTokenHash), findByFamilyIdForUpdate 로 계열의 모든 행을 한 번에
	 *      잠근 뒤, 대상 토큰 행을 그 잠긴 결과 안에서 다시 찾아 client 를 판정한다. 대상 행만 먼저 잠그고
	 *      그다음 계열을 잠그는 경로를 쓰면, 계열을 먼저 잠그는 rotate 와 반대 순서로 잠그게 돼 교착이
	 *      가능해지므로 쓰지 않는다.
	 *
	 * 주의. 첫 조회가 스칼라여야 하는 이유는 rotate 와 같다. 엔티티를 먼저 로드하면 그 인스턴스가 영속성
	 *      컨텍스트에 남아, 잠근 뒤 다시 읽어도 Hibernate 가 잠금 이전 상태를 돌려준다.
	 */
	@Transactional
	public boolean revoke(String refreshToken, String clientId) {
		String tokenHash = tokenGenerator.hash(refreshToken);

		Optional<String> familyId = repository.findFamilyIdByTokenHash(tokenHash);
		if (familyId.isEmpty()) {
			return false;
		}

		List<RefreshTokenEntity> family = repository.findByFamilyIdForUpdate(familyId.get());
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

	/**
	 * 축소 요청(RFC 6749 6)이 저장된 grant 를 벗어나는지 본다. 요청이 비어 있으면 축소하지 않겠다는 뜻이라 항상 통과다.
	 */
	private boolean exceedsGrant(String requestedScope, String storedScopes) {
		if (requestedScope == null || requestedScope.isBlank()) {
			return false;
		}
		List<String> stored = Arrays.asList(storedScopes.split(","));
		return !stored.containsAll(Arrays.asList(requestedScope.trim().split("\\s+")));
	}

	private String toCommaDelimited(String spaceDelimited) {
		return String.join(",", spaceDelimited.trim().split("\\s+"));
	}

	private String toSpaceDelimited(String commaDelimited) {
		return String.join(" ", commaDelimited.split(","));
	}
}
