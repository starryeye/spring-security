package dev.starryeye.custom_social_login_client_with_form_login.service;

import dev.starryeye.custom_social_login_client_with_form_login.exception.UserNotFoundException;
import dev.starryeye.custom_social_login_client_with_form_login.model.User;
import dev.starryeye.custom_social_login_client_with_form_login.model.external_provider.ProviderUser;
import dev.starryeye.custom_social_login_client_with_form_login.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // -- Command -- //
    public void register(String registrationId, ProviderUser providerUser) {

        User user = User.createUser(
                registrationId,
                providerUser.getId(),
                providerUser.getUsername(),
                providerUser.getPassword(),
                providerUser.getProviderId(),
                providerUser.getEmail(),
                providerUser.getAuthorities()
        );

        userRepository.save(user);
    }

    // -- Query -- //
    public User getUserBy(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> new UserNotFoundException(username));
    }

    public boolean existsBy(String username) {
        return userRepository.findByUsername(username).isPresent();
    }
}
