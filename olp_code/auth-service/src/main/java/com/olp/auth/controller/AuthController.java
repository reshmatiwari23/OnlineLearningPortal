package com.olp.auth.controller;

import com.olp.auth.dto.AuthResponse;
import com.olp.auth.dto.LoginRequest;
import com.olp.auth.dto.SignupRequest;
import com.olp.auth.service.AuthService;
import com.olp.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication endpoints.
 * Both endpoints are PUBLIC — no JWT required (configured in SecurityConfig).
 *
 * POST /api/auth/signup → 201 Created
 * POST /api/auth/login  → 200 OK
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * Register a new user account.
     *
     * Request body:
     * {
     *   "email": "user@example.com",
     *   "password": "securePass123",
     *   "name": "Reshma Tiwari",
     *   "role": "instructor"        // optional, defaults to "user"
     * }
     *
     * Response (201):
     * {
     *   "success": true,
     *   "data": { "token": "eyJ...", "userId": "uuid", "email": "...", "role": "...", "expiresIn": 86400 }
     * }
     */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(
            @Valid @RequestBody SignupRequest request) {

        AuthResponse response = authService.signup(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Account created successfully"));
    }

    /**
     * Authenticate an existing user and get a JWT token.
     *
     * Request body:
     * {
     *   "email": "user@example.com",
     *   "password": "securePass123"
     * }
     *
     * Response (200):
     * {
     *   "success": true,
     *   "data": { "token": "eyJ...", "userId": "uuid", "role": "user", "expiresIn": 86400 }
     * }
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Login successful"));
    }
}
