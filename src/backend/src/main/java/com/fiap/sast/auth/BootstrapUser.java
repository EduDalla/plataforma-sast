package com.fiap.sast.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
public class BootstrapUser implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public BootstrapUser(UserRepository users, PasswordEncoder encoder,
            @Value("${sast.bootstrap.email:}") String email,
            @Value("${sast.bootstrap.password:}") String password) {
        this.users = users; this.encoder = encoder;
        this.email = email.trim().toLowerCase(Locale.ROOT); this.password = password;
    }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;
        if (!email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || email.length() > 254
                || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Banco sem usuários: configure SAST_BOOTSTRAP_EMAIL e SAST_BOOTSTRAP_PASSWORD (12 caracteres a 72 bytes).");
        }
        var user = new AppUser(); user.email = email; user.passwordHash = encoder.encode(password);
        users.save(user);
    }
}
