package dev.starryeye.custom_social_login_client_with_form_login.model;

import lombok.Builder;
import lombok.Getter;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Getter
public class OAuth2UserAttributes {

    private final Map<String, Object> mainAttributes;
    private final Map<String, Object> subAttributes;
    private final Map<String, Object> otherAttributes;

    @Builder
    private OAuth2UserAttributes(Map<String, Object> mainAttributes, Map<String, Object> subAttributes, Map<String, Object> otherAttributes) {
        this.mainAttributes = mainAttributes;
        this.subAttributes = subAttributes;
        this.otherAttributes = otherAttributes;
    }

    public static OAuth2UserAttributes ofMain(OAuth2User oAuth2User) {
        return OAuth2UserAttributes.builder()
                .mainAttributes(oAuth2User.getAttributes())
                .subAttributes(Collections.emptyMap())
                .otherAttributes(Collections.emptyMap())
                .build();
    }

    public static OAuth2UserAttributes ofSub(OAuth2User oAuth2User, String subAttributeKey) {
        Map<String, Object> mainAttributes = oAuth2User.getAttributes();

        Map<String, Object> subAttributes = Optional.ofNullable(mainAttributes.get(subAttributeKey))
                .filter(Map.class::isInstance)
                .map(attr -> (Map<String, Object>) attr)
                .orElse(Collections.emptyMap());

        return OAuth2UserAttributes.builder()
                .mainAttributes(mainAttributes)
                .subAttributes(subAttributes)
                .otherAttributes(Collections.emptyMap())
                .build();
    }

    public static OAuth2UserAttributes ofOther(OAuth2User oAuth2User, String subAttributeKey, String otherAttributeKey) {
        Map<String, Object> mainAttributes = oAuth2User.getAttributes();

        Map<String, Object> subAttributes = Optional.ofNullable(mainAttributes.get(subAttributeKey))
                .filter(Map.class::isInstance)
                .map(attr -> (Map<String, Object>) attr)
                .orElse(Collections.emptyMap());

        Map<String, Object> otherAttributes = Optional.ofNullable(subAttributes.get(otherAttributeKey))
                .filter(Map.class::isInstance)
                .map(attr -> (Map<String, Object>) attr)
                .orElse(Collections.emptyMap());

        return OAuth2UserAttributes.builder()
                .mainAttributes(mainAttributes)
                .subAttributes(subAttributes)
                .otherAttributes(otherAttributes)
                .build();
    }
}
