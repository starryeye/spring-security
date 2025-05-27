package dev.starryeye.custom_logout_endpoint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomLogoutEndpointApplication {

	public static void main(String[] args) {
		SpringApplication.run(CustomLogoutEndpointApplication.class, args);
	}

	/**
	 * OpenID Connect 1.0.. 용어 정리.
	 * OP (OpenID Provider)
	 * 		사용자의 인증을 수행하고, ID Token 등을 발급함
	 * 		-> authorization server
	 * RP (Relying Party)
	 * 		OP로부터 인증받고, ID Token을 통해 사용자 정보를 신뢰함
	 * 		-> client
	 */

	/**
	 * https://openid.net/specs/openid-connect-rpinitiated-1_0.html#RPLogout
	 *
	 * 여러 구현 상황을 보며..
	 * RP-Initiated Logout 과 Front-channel Logout, Back-channel Logout 에 대해 알아본다.
	 *
	 * 사전조건
	 * client 가 a.com, b.com, c.com 이 있고 authorization server 는 auth.com 이다.
	 * 모든 client 에서 sso 로 auth.com 세션을 재사용 하면서 로그인을 했다.
	 *
	 * 1. client 들과 OAuth2.0 authorization server 의 logout 과정..
	 * 어떤 client 에서 logout 하면.. 해당 client 에서의 세션(RP 세션)이 종료되고 해당 client 에 발급된 token revoke 시키고 끝난다.
	 * OAuth 2.0 의 authorization server 에는 세션이 없다.(세션이 있을 수 도 있는데 표준 외이다.)
	 *
	 * 2. front-channel, back-channel 구현하지 않은 그냥 oidc authorization server 와 client 들의 logout 과정
	 * a.com 에서 로그아웃을 하면 a.com 의 자체 세션(RP 세션)이 종료되고 a.com 에 발급된 token revoke 시킨다.
	 * 여기서부터는 선택사항,
	 * a.com 에서 authorization server 가 제공하는 logout url 로 리다이렉트 시킬수도 있고 그대로 그냥 끝날 수도 있다.
	 * 만약 리다이렉트를 시킨다면 auth.com 에서 OP 세션을 종료 시킴과 동시에 sso 는 끝날것이다..
	 * 그리고 b.com, c.com 은 로그인이 유지 될 것이다. (b, c client 의 세션 및 token 에 영향이 없음)
	 *
	 * 3. front-channel logout 구현, oidc authorization server 와 client 들의 logout 과정
	 * a.com 에서 로그아웃을 하면 a.com 의 자체 세션(RP 세션)이 종료되고 a.com 에 발급된 token revoke 시킨다.
	 * 그리고 a.com 에서 authorization server 가 제공하는 logout url 로 리다이렉트 시킨다.
	 * auth.com 에서 OP 세션을 종료 시킴과 동시에 sso 는 끝날것이다..
	 * 그리고 사용자의 웹브라우저가 b.com, c.com 으로 logout 을 요청하도록 명령이 되어있는 응답을 보낸다.(iframe 이용)
	 *
	 * 4. backchannel logout 구현, oidc authorization server 와 client 들의 logout 과정
	 * a.com 에서 로그아웃을 하면 a.com 의 자체 세션(RP 세션)이 종료되고 a.com 에 발급된 token revoke 시킨다.
	 * 그리고 a.com 에서 authorization server 가 제공하는 logout url 로 리다이렉트 시킨다.
	 * auth.com 에서 OP 세션을 종료 시킴과 동시에 sso 는 끝날것이다..
	 * 그리고 auth.com이 b.com, c.com 으로 logout 직접 요청을 보내고 사용자 브라우저로 응답보내고 끝난다. (직접 server to server 요청)
	 */

}
