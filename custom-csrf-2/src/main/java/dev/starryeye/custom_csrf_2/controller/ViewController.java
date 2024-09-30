package dev.starryeye.custom_csrf_2.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewController {

    @GetMapping("/script")
    public String script() {
        return "script";
    }

    @GetMapping("/form")
    public String form() {
        /**
         * 타임리프 뷰를 이용하여 csrf 보안을 구현한다.
         * 타임리프 뷰는 JSP 와 다르게 hidden 타입의 input 태그로 csrf 토큰을 전달하는 코드를 자동으로 생성해준다.
         *
         * <form th:action="@{/form/csrfToken}" method="post">
         * 으로 작성해놓으면
         * <form action="/form/csrfToken" method="post">
         *     <input type="hidden" name="_csrf" value="kiCacUWVT1UZQlxdAIR79fjtPcJ90QMO3zg95W6-GyuAjdrQpxGiR3KsKmw0d2tsMKlPk8GOEKAe6WAj7goJ1g2HLU23tei1"/>
         * 가 만들어져서 응답된다.
         */
        return "form";
    }
}
