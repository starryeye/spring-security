package dev.starryeye.custom_social_login_client_with_form_login.model.creator;

import dev.starryeye.custom_social_login_client_with_form_login.model.external_provider.ProviderUser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class DelegatingProviderUserCreator implements ProviderUserCreator<CreateProviderUserRequest, ProviderUser> {

    private final List<ProviderUserCreator<CreateProviderUserRequest, ProviderUser>> converters;

    public DelegatingProviderUserCreator() {
        this.converters = List.of(
                new OAuth2GoogleProviderUserCreator(),
                new OAuth2NaverProviderUserCreator()
        );
    }

    @Override
    public ProviderUser create(CreateProviderUserRequest createProviderUserRequest) {

        if (createProviderUserRequest == null) {
            throw new IllegalArgumentException("createProviderUserRequest cannot be null");
        }

        return converters.stream()
                .map(converter -> converter.create(createProviderUserRequest))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Can't convert " + createProviderUserRequest + " to ProviderUser"));
    }
}
