package dev.starryeye.custom_social_login_client_with_form_login.model.creator;

import dev.starryeye.custom_social_login_client_with_form_login.model.external_provider.ProviderUser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class DelegatingProviderUserConverter implements ProviderUserConverter<CreateProviderUserRequest, ProviderUser> {

    private final List<ProviderUserConverter<CreateProviderUserRequest, ProviderUser>> converters;

    public DelegatingProviderUserConverter() {
        this.converters = List.of(
                new OAuth2GoogleProviderUserConverter(),
                new OAuth2NaverProviderUserConverter()
        );
    }

    @Override
    public ProviderUser convert(CreateProviderUserRequest createProviderUserRequest) {

        if (createProviderUserRequest == null) {
            throw new IllegalArgumentException("createProviderUserRequest cannot be null");
        }

        return converters.stream()
                .map(converter -> converter.convert(createProviderUserRequest))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Can't convert " + createProviderUserRequest + " to ProviderUser"));
    }
}
