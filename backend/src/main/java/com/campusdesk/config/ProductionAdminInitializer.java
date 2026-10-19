package com.campusdesk.config;

import com.campusdesk.domain.Role;
import com.campusdesk.domain.User;
import com.campusdesk.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@Profile("prod")
public class ProductionAdminInitializer {
    @Bean
    CommandLineRunner productionAdminInit(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        @Value("${ADMIN_USERNAME}") String username,
        @Value("${ADMIN_EMAIL}") String email,
        @Value("${ADMIN_FULL_NAME}") String fullName,
        @Value("${ADMIN_PASSWORD}") String password
    ) {
        return args -> {
            if (username.isBlank() || email.isBlank() || fullName.isBlank() || password.length() < 12) {
                throw new IllegalStateException("Production admin settings are invalid; the password must be at least 12 characters");
            }

            User existing = userRepository.findByUsername(username).orElse(null);
            if (existing != null) {
                if (existing.getRole() != Role.ADMIN) {
                    throw new IllegalStateException("Configured production admin username belongs to a non-admin account");
                }
                return;
            }
            if (userRepository.existsByEmail(email)) {
                throw new IllegalStateException("Configured production admin email is already in use");
            }

            User admin = new User();
            admin.setUsername(username);
            admin.setEmail(email);
            admin.setFullName(fullName);
            admin.setPassword(passwordEncoder.encode(password));
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);
        };
    }
}
