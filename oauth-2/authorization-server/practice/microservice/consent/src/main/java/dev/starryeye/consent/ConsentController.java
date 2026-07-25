package dev.starryeye.consent;

import dev.starryeye.consent.dto.ConsentResponse;
import dev.starryeye.consent.dto.SaveConsentRequest;
import dev.starryeye.consent.jpa.ConsentEntity;
import dev.starryeye.consent.jpa.ConsentEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class ConsentController {

	/**
	 * 동의 기록 조회/저장 API 이다. (내부 전용.. gateway 에 노출하지 않는다)
	 *      조회는 기록이 없어도 200 + 빈 scope 로 응답한다. "동의한 적 없음" 은 오류가 아니라 정상 상태다.
	 *      저장은 기존 기록과 합집합으로 병합한다. 추가 동의가 이전 동의를 지우면 안 되기 때문이다.
	 */

	private final ConsentEntityRepository repository;

	@GetMapping("/internal/consents/{sub}/{clientId}")
	public ConsentResponse getConsent(@PathVariable String sub, @PathVariable String clientId) {
		List<String> scopes = repository.findBySubAndClientId(sub, clientId)
				.map(entity -> toList(entity.getScopes()))
				.orElseGet(ArrayList::new);
		return new ConsentResponse(sub, clientId, scopes);
	}

	@PostMapping("/internal/consents")
	public ConsentResponse saveConsent(@RequestBody SaveConsentRequest request) {

		Set<String> merged = new LinkedHashSet<>();
		ConsentEntity entity = repository.findBySubAndClientId(request.sub(), request.clientId()).orElse(null);
		if (entity != null) {
			merged.addAll(toList(entity.getScopes()));
		}
		if (request.scopes() != null) {
			merged.addAll(request.scopes());
		}

		String mergedScopes = String.join(",", merged);
		if (entity == null) {
			entity = ConsentEntity.builder()
					.sub(request.sub()).clientId(request.clientId()).scopes(mergedScopes).build();
		} else {
			entity.replaceScopes(mergedScopes);
		}
		repository.save(entity);

		return new ConsentResponse(request.sub(), request.clientId(), new ArrayList<>(merged));
	}

	private List<String> toList(String commaDelimited) {
		if (!StringUtils.hasText(commaDelimited)) {
			return new ArrayList<>();
		}
		return new ArrayList<>(List.of(StringUtils.commaDelimitedListToStringArray(commaDelimited)));
	}
}
