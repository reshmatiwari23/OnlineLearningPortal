package com.olp.auth.service;

import com.olp.auth.dto.AuthResponse;
import com.olp.auth.dto.LoginRequest;
import com.olp.auth.dto.SignupRequest;
import com.olp.auth.entity.User;
import com.olp.auth.repository.UserRepository;
import com.olp.common.exception.DuplicateResourceException;
import com.olp.common.exception.ResourceNotFoundException;
import com.olp.common.exception.UnauthorisedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core business logic for authentication.
 *
 * Signup flow:
 *   1. Check email not taken
 *   2. Hash password with BCrypt(12)
 *   3. Save user to RDS
 *   4. Create user in Cognito (with rollback if it fails)
 *   5. Authenticate with Cognito to get JWT
 *   6. Return AuthResponse
 *
 * Login flow:
 *   1. Find user by email in RDS
 *   2. Verify BCrypt password
 *   3. Get fresh JWT from Cognito
 *   4. Return AuthResponse
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CognitoService cognitoService;

    // JWT expires in 24 hours = 86400 seconds
    private static final long TOKEN_EXPIRY_SECONDS = 86400L;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        log.info("Signup attempt for email: {}", request.getEmail());

        // Step 1: Check email is not already taken
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                    "An account with email '" + request.getEmail() + "' already exists"
            );
        }

        // Step 2: Hash the password with BCrypt(12)
        // The cost factor 12 means hashing takes ~0.3 seconds — brute force is expensive
        String hashedPassword = passwordEncoder.encode(request.getPassword());

        // Step 3: Save user to RDS
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(hashedPassword)
                .name(request.getName())
                .role(request.getRole())
                .build();

        user = userRepository.save(user);
        log.debug("User saved to RDS: {}", user.getId());

        // Step 4: Create user in Cognito User Pool
        // If this fails, we roll back the RDS record to keep both stores in sync
        try {
            cognitoService.createUser(
                    request.getEmail(),
                    request.getName(),
                    request.getRole(),
                    request.getPassword()
            );
        } catch (Exception e) {
            // Rollback: delete from RDS since Cognito creation failed
            log.error("Cognito createUser failed — rolling back RDS record for: {}", request.getEmail());
            userRepository.delete(user);
            throw e;
        }

        // Step 5: Authenticate with Cognito to get the JWT
        String token = cognitoService.authenticateUser(request.getEmail(), request.getPassword());

        log.info("Signup successful for: {}", request.getEmail());

        // Step 6: Return response
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId().toString())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .expiresIn(TOKEN_EXPIRY_SECONDS)
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        log.info("Login attempt for email: {}", request.getEmail());

        // Step 1: Find user by email in RDS
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorisedException("Invalid email or password"));

        // Step 2: Verify BCrypt password
        // IMPORTANT: always use passwordEncoder.matches() — never compare hashes directly
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorisedException("Invalid email or password");
        }

        // Step 3: Get fresh JWT from Cognito
        String token = cognitoService.authenticateUser(request.getEmail(), request.getPassword());

        log.info("Login successful for: {}", request.getEmail());

        // Step 4: Return response
        return AuthResponse.builder()
                .token(token)
                .userId(user.getId().toString())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole())
                .expiresIn(TOKEN_EXPIRY_SECONDS)
                .build();
    }
}
