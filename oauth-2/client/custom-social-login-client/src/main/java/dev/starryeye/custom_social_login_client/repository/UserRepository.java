package dev.starryeye.custom_social_login_client.repository;

import dev.starryeye.custom_social_login_client.model.User;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class UserRepository {

    private final Map<String, User> users;

    public UserRepository() {
        this.users = new ConcurrentHashMap<>();
    }

    // -- Command -- //
    public void save(User user) {
        users.put(user.getUsername(), user); // 원래 존재하면 덮어쓴다.
    }

    // -- Query -- //
    public Optional<User> findByUsername(String username) {

        if (users.containsKey(username)) {
            return Optional.of(users.get(username));
        }

        return Optional.empty();
    }
}
