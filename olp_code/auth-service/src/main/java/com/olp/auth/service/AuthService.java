package com.olp.auth.service;

import com.olp.auth.dto.AuthResponse;
import com.olp.auth.dto.LoginRequest;
import com.olp.auth.dto.SignupRequest;
import com.olp.auth.entity.User;
import com.olp.auth.repository.UserRepository;
import com.olp.common.exception.DuplicateResourceException;
import com.olp.common.exception.UnauthorisedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core business logic for authentication.
 * Injects CognitoPort — works with both real (aws) and mock (local) implementations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    // Inject the interface — Spring picks the right implementation based on profile
    private final CognitoPort cognitoPort;

    private static final long TOKEN_EXPIRY_SECONDS = 86400L;

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        log.info("Signup: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                    "An account with email '" + request.getEmail() + "' already exists");
        }

        String hashedPassword = passwordEncoder.encode(request.getPassword());

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(hashedPassword)
                .name(request.getName())
                .role(request.getRole())
                .build();

        user = userRepository.save(user);

        try {
            cognitoPort.createUser(
                    request.getEmail(), request.getName(),
                    request.getRole(), request.getPassword());
        } catch (Exception e) {
            log.error("Cognito createUser failed — rolling back RDS: {}", request.getEmail());
            userRepository.delete(user);
            throw e;
        }

        String token = cognitoPort.authenticateUser(request.getEmail(), request.getPassword());

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
        log.info("Login: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorisedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorisedException("Invalid email or password");
        }

        String token = cognitoPort.authenticateUser(request.getEmail(), request.getPassword());

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
