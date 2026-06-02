package com.billing.saas.config;

import com.billing.saas.entity.User;
import com.billing.saas.entity.enums.RoleName;
import com.billing.saas.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SuperAdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.super-admin.email}")
    private String email;

    @Value("${app.super-admin.password}")
    private String password;

    @Value("${app.super-admin.name}")
    private String name;

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            return;
        }

        User user = User.builder()
                .fullName(name)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(RoleName.SUPER_ADMIN)
                .active(true)
                .build();

        userRepository.save(user);
    }
}
