package dev.starryeye.custom_social_login_client_with_form_login.converter;

import dev.starryeye.custom_social_login_client_with_form_login.model.external_provider.ProviderUser;

public class DelegatingProviderUserConverter implements ProviderUserConverter<ProviderUserRequest, ProviderUser> {
    @Override
    public ProviderUser convert(ProviderUserRequest providerUserRequest) {
        return null;
    }
}
