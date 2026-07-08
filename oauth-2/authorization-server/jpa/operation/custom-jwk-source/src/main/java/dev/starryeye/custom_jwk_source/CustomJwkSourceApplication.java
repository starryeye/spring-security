package dev.starryeye.custom_jwk_source;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CustomJwkSourceApplication {

	/**
	 * JWKSource 를 부팅 시 생성(기존 방식)이 아니라 keystore 파일 로드로 구성해본다. (운영 필수 1순위)
	 *
	 * 기존 프로젝트들의 jwkSource 는 부팅 시 RSA 키를 새로 생성했다. 운영 관점에서 두가지 문제가 있다..
	 *      1. 재기동 = 발급된 모든 JWT 무효화
	 *          재기동하면 서명 키가 바뀌므로.. 기존 access token(JWT) 은 리소스 서버의 서명 검증에 전부 실패한다.
	 *          참고. custom-oauth2-authorization-service 프로젝트에서 재기동 후 refresh grant 가 성공했던 것은..
	 *              refresh token 이 서명 검증 없는 불투명 문자열(저장소 조회로만 검증)이기 때문이다.
	 *              토큰 상태를 DB 에 영속화해도 키가 휘발이면 반쪽짜리라는 뜻.
	 *      2. 스케일아웃 불가
	 *          인스턴스마다 다른 키로 서명하므로 A 인스턴스가 발급한 토큰을 B 인스턴스의 JWKS 로 검증할 수 없다.
	 *      -> keystore(PKCS12) 파일에서 키를 로드하면 재기동/다중 인스턴스에서도 같은 키를 사용하게 된다.
	 *
	 * 키 로테이션..
	 *      운영에서는 서명 키를 주기적으로 교체하되, 이전 키로 서명된 (아직 만료되지 않은) 토큰도 검증할 수 있어야 한다.
	 *      -> JWKSource 에 현재 키(개인키 포함)와 이전 키(공개키만)를 함께 담아 JWKS 엔드포인트에 둘 다 노출한다.
	 *          이전 키는 검증용 공개키만 있으면 되므로 toPublicJWK() 로 담는다.
	 *      주의. NimbusJwtEncoder::selectJwk 에서 보면.. 서명 키 선택 시 매칭되는 키가 2개 이상이면 예외를 던진다.
	 *          그래서 OAuth2TokenCustomizer<JwtEncodingContext> 로 JWS 헤더에 현재 키의 kid 를 지정하여 서명 키를 유일하게 특정한다. (AuthorizationServerConfig 참고)
	 *
	 * keystore 생성 명령 (kid 는 alias 로 결정됨.. RSAKey.load 가 alias 를 keyID 로 사용)
	 *      keytool -genkeypair -keyalg RSA -keysize 2048 -validity 3650 -alias key-2026-07-08 \
	 *          -dname "CN=starryeye-auth-server" -keystore token-signing.p12 -storetype PKCS12 -storepass 111111 -keypass 111111
	 *      (날짜 버전 alias 로 키 세대를 관리.. 로테이션 시 새 alias 키를 추가하고 current 설정만 변경)
	 *
	 * 확인 포인트
	 *      1. "/oauth2/jwks" 에 두 키(kid=key-2026-07-08, key-2026-07-01)가 노출되고, 재기동해도 kid 가 동일하다.
	 *      2. 발급된 access token 의 JWT 헤더 kid 가 현재 키(key-2026-07-08)이다.
	 *      3. 서버를 재기동해도 재기동 전에 발급받은 access token 이 "/verify-token" 검증을 통과한다.
	 *          (기존 프로젝트들의 부팅 시 생성 방식이었다면 서명 검증 실패)
	 *
	 * 실행 방법
	 *      1. 서버 기동 후 http/ 의 .http 파일로 authorization code grant 수행
	 *      2. "/verify-token?token={access token}" 으로 검증 (http/api.http)
	 *      3. 서버 재기동 후 같은 token 으로 다시 검증 -> 통과 확인
	 */

	public static void main(String[] args) {
		SpringApplication.run(CustomJwkSourceApplication.class, args);
	}

}
