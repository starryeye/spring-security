package dev.starryeye.custom_social_login_client_with_form_login.model.creator;

public interface ProviderUserConverter<T, R> {

    R convert(T t);
}
