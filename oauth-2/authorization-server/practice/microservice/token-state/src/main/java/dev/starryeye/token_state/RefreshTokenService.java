package dev.starryeye.token_state;

import dev.starryeye.token_state.event.RefreshTokenEventPublisher;
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
	private final RefreshTokenEventPublisher eventPublisher;
	private final long ttlSeconds;
	private final long familyMaxSeconds;

	public RefreshTokenService(
			RefreshTokenEntityRepository repository,
			TokenGenerator tokenGenerator,
			RefreshTokenEventPublisher eventPublisher,
			@Value("${my.refresh-token-ttl-seconds}") long ttlSeconds,
			@Value("${my.refresh-family-max-seconds}") long familyMaxSeconds
	) {
		this.repository = repository;
		this.tokenGenerator = tokenGenerator;
		this.eventPublisher = eventPublisher;
		this.ttlSeconds = ttlSeconds;
		this.familyMaxSeconds = familyMaxSeconds;
	}

	/**
	 * 새 계열을 만들고 첫 토큰을 발급한다. refresh_tokens INSERT 와 ISSUED 사건의 outbox 기록이 한 트랜잭션이라,
	 *      사건을 쓰지 못하면 발급도 롤백된다.
	 */
	@Transactional
	public IssueResult issue(String clientId, String sub, String scope, long authTime, String sid) {
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
				.sid(sid)
				.build();
		repository.save(entity);
		eventPublisher.issued(entity, now);

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
	 *
	 * 주의. 시각(now)은 계열 잠금을 얻은 뒤에 잰다. 메서드 입구에서 재면, 잠금을 기다린 요청의 시각이 그 사이
	 *      커밋된 요청보다 이를 수 있다 — 먼저 도착해 기다리던 재사용의 REVOKED 가 나중에 도착한 회전의 ROTATED 보다
	 *      이른 occurredAt 으로 남아, 감사 이력의 순서가 뒤집힌다. 사건의 occurredAt 뿐 아니라 만료 판정 ·
	 *      consumedAt · 새 토큰의 issuedAt 도 이 시각을 쓴다. 해시 계산과 잠금 없는 스칼라 조회는 시각이 필요
	 *      없어 잠금 앞에 그대로 둔다. 서버가 여럿이면 서버 사이의 시계 차이는 이 규칙이 다루지 않는다.
	 *
	 * 주의. 상태를 바꾼 경로만 사건을 쓴다. 회전 성공은 ROTATED, 재사용 탐지는 REVOKED 하나다. 실패 경로
	 *      (NOT_FOUND · CLIENT_MISMATCH · REVOKED · EXPIRED · SCOPE_EXCEEDED)는 outbox 행도 만들지 않는다 —
	 *      SCOPE_EXCEEDED 의 "아무 상태도 바꾸지 않는다"는 위의 계약에 outbox 도 포함된다.
	 */
	@Transactional
	public RotateResult rotate(String refreshToken, String clientId, String requestedScope) {

		String tokenHash = tokenGenerator.hash(refreshToken);

		Optional<String> familyId = repository.findFamilyIdByTokenHash(tokenHash);
		if (familyId.isEmpty()) {
			return RotateResult.failed(RotateStatus.NOT_FOUND);
		}

		List<RefreshTokenEntity> family = repository.findByFamilyIdForUpdate(familyId.get());
		Instant now = Instant.now();
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
			revokeMembers(family, now, "REUSE_DETECTED");
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
				.sid(entity.getSid()) // 계열 전체가 같은 세션에 속한다
				.build();
		repository.save(rotated);
		eventPublisher.rotated(rotated, now);

		return new RotateResult(
				RotateStatus.ROTATED,
				entity.getSub(),
				toSpaceDelimited(entity.getScopes()),
				entity.getAuthTime(),
				token,
				rotated.getExpiresAt().getEpochSecond(),
				entity.getSid()
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
	 *
	 * 주의. true 는 "이 client 의 토큰을 찾았다"는 뜻이지 "행이 바뀌었다"는 뜻이 아니다. 계열이 이미 전부 끊겨 있어도
	 *      true 를 돌려주고, 그때는 바뀐 행이 없으므로 REVOKED 사건을 쓰지 않는다. 응답은 사건 기록과 별개다.
	 *      시각(now)은 rotate 와 같은 이유로 잠금을 얻은 뒤에 잰다.
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

		Instant now = Instant.now();
		revokeMembers(family, now, "CLIENT_REVOKED");
		return true;
	}

	/**
	 * OP 세션이 끝났을 때 그 세션의 refresh token 을 폐기한다. 로그아웃 이벤트 소비자가 호출한다. 반환값은 이 호출이
	 *      실제로 REVOKED 로 바꾼 행 수의 합이다.
	 *
	 * 주의. 폐기 범위가 sid 단위다. sub 로 죽이면 같은 사용자가 다른 브라우저에서 만든 세션까지 함께
	 *      죽고, client_id 까지 묶으면 한 세션에 붙은 다른 RP 가 남는다. oidc_sessions 를 sid 로 지우는
	 *      범위와 정확히 같아야 두 저장소가 같은 단위로 움직인다.
	 *
	 * 주의. 잠금 경로가 rotate 와 같다. 이 sid 의 ACTIVE 행을 가진 계열의 id 를 잠금 없는 스칼라 조회
	 *      (findFamilyIdsBySidAndStatus)로 familyId 순으로 찾고, 계열마다 findByFamilyIdForUpdate 로 계열 전체를
	 *      잠근 뒤, 그 잠긴 결과 안에서 이 sid 의 ACTIVE 행을 골라 폐기한다. 첫 조회가 스칼라여야 하는 이유는
	 *      rotate 와 같다 — 엔티티를 먼저 로드하면 그 인스턴스가 영속성 컨텍스트에 남아, 잠근 뒤 다시 읽어도
	 *      Hibernate 가 잠금 이전 상태를 돌려준다.
	 *
	 * 주의. sid 로 직접 잠그지 않는다. refresh_tokens 에는 sid 인덱스가 없어 sid 조건의 잠금 조회는 전체 스캔이
	 *      되고, InnoDB 의 REPEATABLE READ 에서 locking read 는 스캔한 행을 모두 잠근다 — 로그아웃 하나가 다른
	 *      사용자의 회전까지 멈추게 된다. MySQL 문서의 locking read 규칙에서 나오는 결론이며 측정한 값은 아니다.
	 *      계열 잠금 하나로 가면 findByFamilyIdForUpdate 주석의 교착 부재 논증(모든 호출부가 같은 잠금 경로를
	 *      쓴다)도 그대로 선다.
	 *
	 * 주의. 시각(now)은 계열마다 그 계열의 잠금을 얻은 뒤에 잰다. 이유는 rotate 와 같다. 다만 이 위치를 가려내는
	 *      테스트는 없다 — RefreshTokenServiceMySqlLockSemanticsTest 의 로그아웃은 회전이 시각을 잰 뒤에 들어오므로,
	 *      시각을 메서드 입구에서 재도 그 테스트의 순서 단언이 성립한다.
	 *
	 * 주의. 사건은 실제로 바뀐 행만 말한다. 계열마다 바뀐 행이 하나 이상이면 그 계열의 REVOKED(SESSION_LOGGED_OUT)
	 *      하나를 쓴다. 같은 로그아웃이 다시 오면(Kafka 재전달) 바꿀 ACTIVE 행이 없어 사건도 없다 — 소비자 쪽에
	 *      별도 중복 처리 표가 필요 없다. CONSUMED 행은 건드리지 않으므로, 그 뒤 옛 토큰이 다시 오면 rotate 의
	 *      재사용 탐지가 남은 행을 끊고 REVOKED(REUSE_DETECTED) 를 따로 쓴다.
	 *
	 * 주의. activeTokensRevoked 는 상태 기준이다. 만료 시각이 지났어도 상태가 ACTIVE 인 행은 ACTIVE 로 센다.
	 *
	 * 주의. InnoDB 에서 한 계열을 두고 회전과 겹치는 두 순서는 직렬화된다 — RefreshTokenServiceMySqlLockSemanticsTest 가
	 *      순서마다 한쪽을 잠금을 쥔 채 세워 두고 다른 쪽의 잠금 대기를 확인한 뒤 결과를 단언한다. 회전이 잠금을 쥔 동안
	 *      기다린 로그아웃은 깨어난 뒤의 findByFamilyIdForUpdate 에서 회전이 삽입한 새 ACTIVE 행을 보고 그 행을 끊는다
	 *      (원래 행은 CONSUMED 그대로). 로그아웃이 잠금을 쥔 동안 기다린 회전은 끊긴 계열을 보고 REVOKED 로 거절된다.
	 *      두 순서 모두 교착 없이 끝난다. 계열을 잠그지 않고 findByFamilyId 로 읽으면 첫 순서에서 로그아웃이 회전 전
	 *      스냅샷으로 판정해 새 행을 놓친다(새 행은 ACTIVE 로 남는다). 이 성질은 InnoDB 기준이다 — h2 에서는 대기 중
	 *      삽입된 형제 행이 잠금 재조회에서 빠질 수 있다(RefreshTokenServiceConcurrentRotateTest 주석). 한 로그아웃이
	 *      여러 계열을 잠그는 동안의 경합과 인접한 계열 사이의 gap lock 상호작용은 이 테스트들이 보이지 않는다.
	 *
	 * 주의. 남는 빈틈이 있다. 첫 조회 뒤에 같은 sid 로 새 계열이 생기면(로그아웃 직전에 받은 code 의 교환) 이 호출은
	 *      그 계열을 모른다 — 계열 목록은 첫 조회에서 이미 정해졌고, 새 계열은 이 호출이 잠그는 어느 계열과도
	 *      무관하다. 코드 구조에서 나오는 결론이며 이를 재현하는 테스트는 없다.
	 */
	@Transactional
	public int revokeBySid(String sid) {
		int revoked = 0;
		for (String familyId : repository.findFamilyIdsBySidAndStatus(sid, RefreshTokenStatus.ACTIVE)) {
			List<RefreshTokenEntity> family = repository.findByFamilyIdForUpdate(familyId);
			Instant now = Instant.now();
			List<RefreshTokenEntity> active = family.stream()
					.filter(member -> member.getStatus() == RefreshTokenStatus.ACTIVE && sid.equals(member.getSid()))
					.toList();
			revoked += revokeMembers(active, now, "SESSION_LOGGED_OUT");
		}
		return revoked;
	}

	/**
	 * members 를 폐기하고, 실제로 바뀐 행이 하나 이상이면 REVOKED 사건을 쓴 뒤 바뀐 행 수를 돌려준다.
	 *      revokedTokens 는 바뀐 행 수, activeTokensRevoked 는 그중 바뀌기 직전에 ACTIVE 였던 수다.
	 *      재사용 탐지 · client 폐기(RFC 7009) · 로그아웃 폐기(revokeBySid)가 부른다.
	 *
	 * 주의. 바뀐 행이 0이면 사건을 쓰지 않는다(members 가 비어 있어도 같다). 이미 전부 끊긴 계열에 폐기가 다시
	 *      와도 감사 이력에 "끊김"이 한 번 더 남지 않는다. 이 가드는 이 메서드 한 곳에만 있다 — 계열을 끊는
	 *      경로가 이 메서드를 거쳐야 같은 규칙이 적용된다.
	 *
	 * 주의. 사건의 계열 · 주체 · client · 세션은 members.get(0) 에서 읽는다. 한 계열의 행은 이 값이 모두 같다 —
	 *      rotate 가 기존 행의 값을 새 행에 복사한다. 서로 다른 계열의 행을 한 목록에 섞어 넘기면 안 된다.
	 *
	 * 주의. members 는 계열 잠금(findByFamilyIdForUpdate) 뒤에 읽은 행이어야 한다. 바뀐 행 수가 사건에 실리는
	 *      수치라서, 잠금 없이 읽은 오래된 상태로 세면 수치가 실제와 달라진다.
	 */
	private int revokeMembers(List<RefreshTokenEntity> members, Instant at, String reason) {
		int revokedTokens = 0;
		int activeTokensRevoked = 0;
		for (RefreshTokenEntity member : members) {
			boolean wasActive = member.getStatus() == RefreshTokenStatus.ACTIVE;
			if (member.revoke(at, reason)) {
				revokedTokens++;
				if (wasActive) {
					activeTokensRevoked++;
				}
			}
		}
		if (revokedTokens == 0) {
			return 0;
		}
		repository.saveAll(members);
		eventPublisher.revoked(members.get(0), at, reason, revokedTokens, activeTokensRevoked);
		return revokedTokens;
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
