package dev.starryeye.custom_social_login_client_with_form_login.model.external_provider;

import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;

import java.util.Map;

public interface ProviderOidcUser {

    Map<String, Object> getClaims();
    OidcUserInfo getUserInfo();
    OidcIdToken getIdToken();
}
