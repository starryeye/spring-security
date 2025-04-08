package dev.starryeye.custom_social_login_client_with_form_login.service.security.creator;

public interface ProviderUserCreator<T, R> {

    R create(T t);
}
