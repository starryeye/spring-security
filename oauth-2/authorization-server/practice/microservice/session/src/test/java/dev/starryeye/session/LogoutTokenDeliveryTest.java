package dev.starryeye.session;

import dev.starryeye.session.client.ClientInfo;
import dev.starryeye.session.client.ClientRegistryClient;
import dev.starryeye.session.client.SigningClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

// deliver() 하나가 실제 프로덕션 경로다: client 조회 -> backchannel_logout_uri 게이트 -> 서명 위임 ->
// form POST. RestClient.Builder 는 MockRestServiceServer 로 바인딩해 실제 HTTP 계약(URI·메서드·
// content-type·파라미터명)을 검증하고, ClientRegistryClient·SigningClient 는 원격 호출이라 mock 한다.
class LogoutTokenDeliveryTest {

	private final ClientRegistryClient clientRegistryClient = mock(ClientRegistryClient.class);
	private final SigningClient signingClient = mock(SigningClient.class);
	private final LogoutTokenFactory logoutTokenFactory = new LogoutTokenFactory("http://localhost:9000");

	private MockRestServiceServer server;

	private LogoutTokenDelivery newDelivery() {
		RestClient.Builder builder = RestClient.builder();
		server = MockRestServiceServer.bindTo(builder).build();
		return new LogoutTokenDelivery(clientRegistryClient, signingClient, logoutTokenFactory, builder);
	}

	// 사용자 세션이 없는 client_credentials 전용 client 는 backchannel_logout_uri 가 비어 있다.
	// 서명도, POST 도 일어나면 안 된다 — 실제로 일어나면 존재하지 않는 곳으로 요청을 보내거나 헛수고를 한다.
	@Test
	void skipsClientsWithoutBackchannelLogoutUri() {
		when(clientRegistryClient.getClient("article-api")).thenReturn(new ClientInfo("article-api", ""));
		LogoutTokenDelivery delivery = newDelivery();
		// 기대 요청을 등록하지 않는다 — 실제로 요청이 나가면 MockRestServiceServer 가 예외를 던진다.

		delivery.deliver("SID-1", "user-sub-0001", "article-api");

		verifyNoInteractions(signingClient);
		server.verify();
	}

	@Test
	void postsLogoutTokenAsFormEncodedBodyToTheRegisteredUri() {
		String uri = "http://localhost:8095/logout/connect/back-channel/microservice";
		when(clientRegistryClient.getClient("demo-rp")).thenReturn(new ClientInfo("demo-rp", uri));
		when(signingClient.sign(anyMap(), anyString())).thenReturn("signed.logout.token");
		LogoutTokenDelivery delivery = newDelivery();

		MultiValueMap<String, String> expectedForm = new LinkedMultiValueMap<>();
		expectedForm.add("logout_token", "signed.logout.token");
		server.expect(requestTo(uri))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().contentType(MediaType.APPLICATION_FORM_URLENCODED))
				.andExpect(content().formData(expectedForm))
				.andRespond(withSuccess());

		delivery.deliver("SID-1", "user-sub-0001", "demo-rp");

		server.verify();
	}

	// LogoutTokenFactory 의 claim 계약과 LOGOUT_TOKEN_TYP 이 실제로 서명 요청에 실리는지 확인한다.
	// signingClient 를 mock 해 넘어간 인자를 그대로 검사한다 — 대상이 mock 이라도, deliver() 가 그 인자를
	// 무엇으로 채우는지가 검증 대상이라 실제 프로덕션 코드(create 호출·typ 상수 사용)가 실행된다.
	@Test
	void signsWithLogoutTokenFactoryClaimsAndTheLogoutTokenTyp() {
		String uri = "http://localhost:8095/logout/connect/back-channel/microservice";
		when(clientRegistryClient.getClient("demo-rp")).thenReturn(new ClientInfo("demo-rp", uri));
		when(signingClient.sign(anyMap(), anyString())).thenReturn("signed.logout.token");
		LogoutTokenDelivery delivery = newDelivery();
		server.expect(requestTo(uri)).andRespond(withSuccess());

		delivery.deliver("SID-1", "user-sub-0001", "demo-rp");

		@SuppressWarnings("unchecked")
		ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
		ArgumentCaptor<String> typCaptor = ArgumentCaptor.forClass(String.class);
		verify(signingClient).sign(claimsCaptor.capture(), typCaptor.capture());

		assertThat(claimsCaptor.getValue())
				.containsEntry("iss", "http://localhost:9000")
				.containsEntry("sub", "user-sub-0001")
				.containsEntry("aud", "demo-rp")
				.containsEntry("sid", "SID-1");
		assertThat(typCaptor.getValue()).isEqualTo(LogoutTokenFactory.LOGOUT_TOKEN_TYP);
	}
}
