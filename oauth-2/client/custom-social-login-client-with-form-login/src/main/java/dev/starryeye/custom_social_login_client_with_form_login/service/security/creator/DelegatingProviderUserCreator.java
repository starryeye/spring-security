package dev.starryeye.custom_social_login_client_with_form_login.service.security.creator;

import dev.starryeye.custom_social_login_client_with_form_login.model.ProviderUser;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

@Component
public class DelegatingProviderUserCreator implements ProviderUserCreator<CreateProviderUserRequest, ProviderUser> {

    private final List<ProviderUserCreator<CreateProviderUserRequest, ProviderUser>> creators;

    public DelegatingProviderUserCreator(List<ProviderUserCreator<CreateProviderUserRequest, ProviderUser>> creators) {
        this.creators = creators;
    }

    @Override
    public ProviderUser create(CreateProviderUserRequest createProviderUserRequest) {

        if (createProviderUserRequest == null) {
            throw new IllegalArgumentException("createProviderUserRequest cannot be null");
        }

        return creators.stream()
                .map(creator -> creator.create(createProviderUserRequest))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Can't convert " + createProviderUserRequest + " to ProviderUser"));
    }
}
