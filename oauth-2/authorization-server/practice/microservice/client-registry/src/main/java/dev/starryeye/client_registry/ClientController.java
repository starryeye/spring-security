package dev.starryeye.client_registry;

import dev.starryeye.client_registry.dto.ClientResponse;
import dev.starryeye.client_registry.jpa.ClientEntity;
import dev.starryeye.client_registry.jpa.ClientEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ClientController {

	/**
	 * client 조회 API.. 조회 결과를 "clients" 캐시에 담는다. (CacheConfig 의 TTL 30초)
	 */

	private final ClientLookupService lookupService;

	@GetMapping("/internal/clients/{clientId}")
	public ClientResponse getClient(@PathVariable String clientId) {
		return lookupService.findByClientId(clientId);
	}

	@Service
	@RequiredArgsConstructor
	static class ClientLookupService {

		private final ClientEntityRepository repository;

		// @Cacheable 은 같은 빈 내부 호출에서는 동작하지 않으므로 별도 빈으로 분리한다.
		@Cacheable("clients")
		public ClientResponse findByClientId(String clientId) {
			ClientEntity entity = repository.findById(clientId)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
			return new ClientResponse(
					entity.getClientId(),
					List.of(StringUtils.commaDelimitedListToStringArray(entity.getRedirectUris())),
					List.of(StringUtils.commaDelimitedListToStringArray(entity.getScopes())),
					entity.getClientSecretHash(),
					List.of(StringUtils.commaDelimitedListToStringArray(entity.getGrantTypes()))
			);
		}
	}
}
