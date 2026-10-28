package dev.starryeye.audit.query;

import dev.starryeye.audit.jpa.RefreshTokenEventEntity;
import dev.starryeye.audit.jpa.RefreshTokenEventEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RefreshTokenFamilyQuery {

	/**
	 * 한 sub 의 refresh 계열 사건을 계열별로 묶어 요약과 함께 돌려준다.
	 *
	 * 주의. 저장소가 사건을 occurredAt → id 순으로 돌려주므로, familyId 로 묶을 때 첫 등장 순서를 지키면 계열 안의
	 *      사건도 그 순서 그대로다. RefreshTokenFamilyView.from 은 이 순서를 믿고 다시 정렬하지 않는다.
	 *
	 * 주의. 계열끼리는 issuedAt 내림차순(최근 발급이 위)이고, issuedAt 이 없는 계열(ISSUED 사건이 없다)은 첫 사건의
	 *      occurredAt 을 대신 쓴다. 같으면 familyId 오름차순이라 응답 순서가 결정적이다.
	 *
	 * 주의. 페이지를 나누지 않는다. 저장소가 한 sub 의 사건을 전부 읽는다(RefreshTokenEventEntityRepository 참고).
	 */

	private static final Comparator<RefreshTokenFamilyView> NEWEST_FIRST =
			Comparator.comparing(RefreshTokenFamilyQuery::sortTime, Comparator.reverseOrder())
					.thenComparing(RefreshTokenFamilyView::familyId);

	private final RefreshTokenEventEntityRepository repository;

	public RefreshTokenFamiliesResponse familiesOf(String sub, Instant asOf) {
		Map<String, List<RefreshTokenEventEntity>> byFamily = new LinkedHashMap<>();
		for (RefreshTokenEventEntity event : repository.findBySubOrderByOccurredAtAscIdAsc(sub)) {
			byFamily.computeIfAbsent(event.getFamilyId(), familyId -> new ArrayList<>()).add(event);
		}

		List<RefreshTokenFamilyView> families = byFamily.values().stream()
				.map(events -> RefreshTokenFamilyView.from(events, asOf))
				.sorted(NEWEST_FIRST)
				.toList();

		return new RefreshTokenFamiliesResponse(sub, asOf, families);
	}

	private static Instant sortTime(RefreshTokenFamilyView family) {
		return (family.issuedAt() != null) ? family.issuedAt() : family.events().get(0).occurredAt();
	}
}
