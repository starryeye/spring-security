package dev.starryeye.custom_authorize_pre_post_authorize;

import org.springframework.security.access.expression.method.MethodSecurityExpressionOperations;
import org.springframework.stereotype.Component;

@Component
public class MyAuthorizer {

    public boolean isUser(MethodSecurityExpressionOperations root) { //"root" 말고 다른 이름은 왜 안되지..

        return root.hasAuthority("ROLE_USER");
    }
}
