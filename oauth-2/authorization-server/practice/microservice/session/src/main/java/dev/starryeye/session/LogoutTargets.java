package dev.starryeye.session;

import java.util.List;

public record LogoutTargets(List<Target> targets) {

	/**
	 * 한 RP(clientId)와 그 RP 세션의 실제 주체(sub)를 한 쌍으로 나른다.
	 *
	 * 주의. 같은 sid 아래 여러 행이 서로 다른 sub 를 가질 수 있다(재로그인이 sid 를 재사용한 적이 있었다면).
	 *      logout token 의 sub 는 그 RP 세션의 주체여야 하므로, 대표값 하나를 모든 RP 에 재사용하면 안 되고
	 *      반드시 그 행의 sub 를 그 RP 에게만 실어야 한다.
	 */
	public record Target(String clientId, String sub) {
	}
}
