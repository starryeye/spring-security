package dev.starryeye.resource_server_comment.api.controller;

import dev.starryeye.resource_server_comment.api.controller.request.GetCommentsRequest;
import dev.starryeye.resource_server_comment.api.service.CommentService;
import dev.starryeye.resource_server_comment.dto.Comment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @PostMapping("/comments")
    public List<Comment> comments(@RequestBody GetCommentsRequest request, JwtAuthenticationToken authentication) {

        /**
         * 교환된 토큰의 흔적 관찰용 감사 로그.. (relay 버전과의 차이가 토큰에 그대로 보인다)
         *      sub : 여전히 사용자.. 누구를 위한 요청인지 유지된다.
         *      act : 대신 호출한 주체(my-article-client).. relay 였다면 남지 않는 정보다.
         *      aud : 교환 client.. 사용자 원본 토큰이 아니라 이 호출용으로 발급된 토큰임을 뜻한다.
         *      scope : comment 뿐.. 사용자 토큰의 나머지 scope 는 여기까지 오지 않는다.
         */
        Jwt jwt = authentication.getToken();
        log.info("[감사] sub={}, act={}, aud={}, scope={}",
                jwt.getSubject(), jwt.getClaims().get("act"), jwt.getAudience(), jwt.getClaims().get("scope"));

        return commentService.getCommentsBy(request.contentId());
    }
}
