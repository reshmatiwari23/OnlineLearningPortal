package com.olp.auth.config.local;

import com.olp.auth.entity.User;
import com.olp.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Pre-loads test users into H2 on startup (local profile only).
 *
 * This means you never need to manually signup before testing.
 * H2 resets on every restart — this class runs again automatically.
 *
 * Test accounts created:
 *   Learner:     learner@olp.com    / Test1234!
 *   Instructor:  instructor@olp.com / Test1234!
 */
@Configuration
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner loadTestData() {
        return args -> {
            // Create test learner
            if (!userRepository.existsByEmail("learner@olp.com")) {
                userRepository.save(User.builder()
                        .email("learner@olp.com")
                        .passwordHash(passwordEncoder.encode("Test1234!"))
                        .name("Test Learner")
                        .role("user")
                        .build());
                log.info("=================================================");
                log.info("TEST ACCOUNTS CREATED:");
                log.info("  Learner:     learner@olp.com    / Test1234!");
                log.info("  Instructor:  instructor@olp.com / Test1234!");
                log.info("=================================================");
            }

            // Create test instructor
            if (!userRepository.existsByEmail("instructor@olp.com")) {
                userRepository.save(User.builder()
                        .email("instructor@olp.com")
                        .passwordHash(passwordEncoder.encode("Test1234!"))
                        .name("Test Instructor")
                        .role("instructor")
                        .build());
            }
        };
    }
}
