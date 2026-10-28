package dev.starryeye.token_state;

import dev.starryeye.token_state.jpa.RefreshTokenEntity;
import dev.starryeye.token_state.jpa.RefreshTokenEntityRepository;

public final class RefreshTokenFixtures {

	/**
	 * refresh_tokens 행을 테스트 조건에 맞게 바꿔 놓는 도우미다.
	 *
	 * 주의. 엔티티에는 만료 시각을 바꾸는 메서드가 없다. 그래서 같은 해시로 만료만 다른 행을 새로 만들어 기존 행을
	 *      지우고 저장한다 — 돌려받은 행은 새 id 를 갖는다.
	 */

	private RefreshTokenFixtures() {
	}

	/**
	 * 개별 만료(expires_at)만 과거로 옮긴 같은 해시의 행으로 바꿔 저장하고, 그 행을 돌려준다. family_expires_at 은
	 *      그대로 미래라, isExpired 의 두 항 중 개별 항만 걸린다.
	 */
	public static RefreshTokenEntity expireIndividualToken(RefreshTokenEntityRepository repository,
			RefreshTokenEntity entity) {
		RefreshTokenEntity replaced = RefreshTokenEntity.builder()
				.tokenHash(entity.getTokenHash())
				.familyId(entity.getFamilyId())
				.clientId(entity.getClientId())
				.sub(entity.getSub())
				.scopes(entity.getScopes())
				.authTime(entity.getAuthTime())
				.issuedAt(entity.getIssuedAt())
				.expiresAt(entity.getIssuedAt().minusSeconds(1))
				.familyExpiresAt(entity.getFamilyExpiresAt())
				.sid(entity.getSid())
				.build();
		repository.delete(entity);
		repository.flush();
		return repository.save(replaced);
	}
}
