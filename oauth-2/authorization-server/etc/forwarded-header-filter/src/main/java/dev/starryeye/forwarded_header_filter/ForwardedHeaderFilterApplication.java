package dev.starryeye.forwarded_header_filter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ForwardedHeaderFilterApplication {

	/**
	 * 리버스 프록시(LB) 뒤에서 authorization server 를 운영할 때의 issuer/URL 일관성 문제와 ForwardedHeaderFilter 를 알아본다.
	 *
	 * 문제..
	 *      운영에서 authorization server 는 보통 리버스 프록시/LB 뒤에 있다. (client 는 https://auth.example.com, 서버는 내부 주소)
	 *      서버 입장에서 요청의 Host 는 프록시가 보낸 내부 주소라서..
	 *      issuer, 메타데이터의 endpoint URL, redirect 등이 전부 내부 주소 기준으로 만들어져 버린다.
	 *          (기존 프로젝트들은 issuer 를 고정값으로 명시해서 이 문제가 가려져 있었다..
	 *           이 프로젝트는 issuer 를 설정하지 않아 요청 기반으로 유도되도록 하여 문제를 드러낸다. AuthorizationServerConfig 참고)
	 *
	 * 해결.. ForwardedHeaderFilter
	 *      프록시가 전달해주는 X-Forwarded-Host / X-Forwarded-Proto / X-Forwarded-For 헤더를 읽어..
	 *      요청 객체(HttpServletRequest)의 host, scheme 등을 원 요청 기준으로 재구성해주는 filter 이다. (spring-web 제공)
	 *      security filter 나 컨트롤러가 요청 정보를 읽기 전에 동작해야 하므로 최우선 순위로 등록한다. (ForwardedHeaderFilterConfig 참고)
	 *      참고. application.yml 의 server.forward-headers-strategy=framework 설정으로도 동일한 filter 가 자동 등록된다.
	 *
	 * 보안 주의..
	 *      ForwardedHeaderFilter 는 X-Forwarded-* 헤더를 신뢰하고 그대로 반영한다.
	 *      외부 클라이언트가 조작한 헤더가 프록시를 통과해 들어오면 안되므로..
	 *      프록시에서 X-Forwarded-* 를 항상 덮어쓰도록 설정해야 한다. (docker-compose/nginx.conf 의 proxy_set_header 참고)
	 *
	 * 확인 포인트 (nginx 가 host 9000 포트에서 8091 로 프록시)
	 *      1. 필터 켠 상태(my.forwarded-header-filter.enabled=true)에서..
	 *          직접 접근(8091) 메타데이터의 issuer = http://localhost:8091
	 *          프록시 접근(9000) 메타데이터의 issuer = http://localhost:9000  <- X-Forwarded-Host 반영
	 *      2. 프록시(9000)만 거쳐서 로그인 -> consent -> code -> token 전체 flow 가 완주되고, 발급된 JWT 의 iss 도 9000 기준이다.
	 *      3. 필터 끄고(enabled=false) 재기동하면.. 프록시 접근인데 issuer 가 내부 주소(host.docker.internal:8091)로 나온다. (문제 재현)
	 *
	 * 실행 방법
	 *      1. docker-compose/docker-compose.yml 로 nginx 기동
	 *      2. 서버 기동 후 http://localhost:9000 (프록시) 로 http/ 의 요청 수행
	 */

	public static void main(String[] args) {
		SpringApplication.run(ForwardedHeaderFilterApplication.class, args);
	}

}
