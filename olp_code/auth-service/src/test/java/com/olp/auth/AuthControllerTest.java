package com.olp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.auth.controller.AuthController;
import com.olp.auth.dto.AuthResponse;
import com.olp.auth.dto.LoginRequest;
import com.olp.auth.dto.SignupRequest;
import com.olp.auth.service.AuthService;
import com.olp.common.exception.DuplicateResourceException;
import com.olp.common.exception.UnauthorisedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for AuthController.
 * Uses @WithMockUser to bypass Spring Security authentication
 * so tests focus on controller logic only.
 */
@WebMvcTest(AuthController.class)
@WithMockUser  // bypasses Spring Security 401/403 for all tests
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    // ── Helper ──────────────────────────────────────────────────
    private AuthResponse validAuthResponse() {
        return AuthResponse.builder()
                .token("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.test")
                .userId("550e8400-e29b-41d4-a716-446655440000")
                .email("test@example.com")
                .name("Test User")
                .role("user")
                .expiresIn(86400L)
                .build();
    }

    // ── Signup tests ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /signup → 201 when valid request")
    void signup_success() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setEmail("test@example.com");
        request.setPassword("securePass123");
        request.setName("Test User");
        request.setRole("user");

        when(authService.signup(any(SignupRequest.class))).thenReturn(validAuthResponse());

        mockMvc.perform(post("/api/auth/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.role").value("user"));
    }

    @Test
    @DisplayName("POST /signup → 409 when email already registered")
    void signup_duplicate_email() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setEmail("existing@example.com");
        request.setPassword("securePass123");
        request.setName("Test User");

        when(authService.signup(any(SignupRequest.class)))
                .thenThrow(new DuplicateResourceException("Email already registered"));

        mockMvc.perform(post("/api/auth/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    @DisplayName("POST /signup → 400 when email is invalid format")
    void signup_invalid_email() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setEmail("not-an-email");
        request.setPassword("securePass123");
        request.setName("Test User");

        mockMvc.perform(post("/api/auth/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /signup → 400 when password is too short")
    void signup_short_password() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setEmail("test@example.com");
        request.setPassword("abc");
        request.setName("Test User");

        mockMvc.perform(post("/api/auth/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("POST /signup → 400 when name is blank")
    void signup_blank_name() throws Exception {
        SignupRequest request = new SignupRequest();
        request.setEmail("test@example.com");
        request.setPassword("securePass123");
        request.setName("");

        mockMvc.perform(post("/api/auth/signup")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── Login tests ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /login → 200 with token when credentials are valid")
    void login_success() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("securePass123");

        when(authService.login(any(LoginRequest.class))).thenReturn(validAuthResponse());

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresIn").value(86400));
    }

    @Test
    @DisplayName("POST /login → 401 when password is wrong")
    void login_wrong_password() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setEmail("test@example.com");
        request.setPassword("wrongPassword");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new UnauthorisedException("Invalid email or password"));

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("POST /login → 400 when email is missing")
    void login_missing_email() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setPassword("securePass123");

        mockMvc.perform(post("/api/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.email").exists());
    }
}
