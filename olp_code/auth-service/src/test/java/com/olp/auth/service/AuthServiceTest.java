package com.olp.auth.service;

import com.olp.auth.dto.AuthResponse;
import com.olp.auth.dto.LoginRequest;
import com.olp.auth.dto.SignupRequest;
import com.olp.auth.entity.User;
import com.olp.auth.repository.UserRepository;
import com.olp.common.exception.DuplicateResourceException;
import com.olp.common.exception.UnauthorisedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CognitoService cognitoService;

    @InjectMocks
    private AuthService authService;

    private SignupRequest signupRequest;
    private LoginRequest loginRequest;
    private User savedUser;
    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_PASSWORD = "Password123!";
    private static final String TEST_NAME = "Test User";
    private static final String TEST_TOKEN = "eyJraWQiOiJ0ZXN0SldU.eyJzdWIiOiJ1c2VyMTIzIn0.signature";
    private static final UUID TEST_USER_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        signupRequest = new SignupRequest();
        signupRequest.setEmail(TEST_EMAIL);
        signupRequest.setPassword(TEST_PASSWORD);
        signupRequest.setName(TEST_NAME);
        signupRequest.setRole("user");

        loginRequest = new LoginRequest();
        loginRequest.setEmail(TEST_EMAIL);
        loginRequest.setPassword(TEST_PASSWORD);

        savedUser = User.builder()
                .id(TEST_USER_ID)
                .email(TEST_EMAIL)
                .passwordHash("$2a$12$hashedPassword")
                .name(TEST_NAME)
                .role("user")
                .build();
    }

    @Nested
    @DisplayName("signup()")
    class SignupTests {

        @Test
        @DisplayName("should create user successfully when email is new")
        void signup_success() {
            when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn("$2a$12$hashedPassword");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(cognitoService.authenticateUser(TEST_EMAIL, TEST_PASSWORD)).thenReturn(TEST_TOKEN);

            AuthResponse response = authService.signup(signupRequest);

            assertThat(response).isNotNull();
            assertThat(response.getEmail()).isEqualTo(TEST_EMAIL);
            assertThat(response.getName()).isEqualTo(TEST_NAME);
            assertThat(response.getRole()).isEqualTo("user");
            assertThat(response.getToken()).isEqualTo(TEST_TOKEN);
            assertThat(response.getUserId()).isEqualTo(TEST_USER_ID.toString());

            verify(userRepository).existsByEmail(TEST_EMAIL);
            verify(passwordEncoder).encode(TEST_PASSWORD);
            verify(userRepository).save(any(User.class));
            verify(cognitoService).createUser(TEST_EMAIL, TEST_NAME, "user");
            verify(cognitoService).authenticateUser(TEST_EMAIL, TEST_PASSWORD);
        }

        @Test
        @DisplayName("should throw DuplicateResourceException when email already registered")
        void signup_duplicateEmail_throwsException() {
            when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(true);

            assertThatThrownBy(() -> authService.signup(signupRequest))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining(TEST_EMAIL);

            verify(userRepository).existsByEmail(TEST_EMAIL);
            verify(userRepository, never()).save(any());
            verify(cognitoService, never()).createUser(any(), any(), any());
        }

        @Test
        @DisplayName("should rollback RDS save when Cognito createUser fails")
        void signup_cognitoFailure_rollsBack() {
            when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn("$2a$12$hashedPassword");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            doThrow(new RuntimeException("Cognito error"))
                    .when(cognitoService).createUser(anyString(), anyString(), anyString());

            assertThatThrownBy(() -> authService.signup(signupRequest))
                    .isInstanceOf(RuntimeException.class);

            verify(userRepository).save(any(User.class));
            verify(cognitoService).createUser(TEST_EMAIL, TEST_NAME, "user");
            verify(cognitoService, never()).authenticateUser(any(), any());
        }

        @Test
        @DisplayName("should set default role to 'user' when role not provided")
        void signup_noRole_defaultsToUser() {
            signupRequest.setRole(null);
            when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);
            when(cognitoService.authenticateUser(any(), any())).thenReturn(TEST_TOKEN);

            authService.signup(signupRequest);

            verify(cognitoService).createUser(eq(TEST_EMAIL), eq(TEST_NAME), eq("user"));
        }

        @Test
        @DisplayName("should allow instructor role during signup")
        void signup_instructorRole_success() {
            signupRequest.setRole("instructor");
            User instructorUser = User.builder()
                    .id(TEST_USER_ID).email(TEST_EMAIL)
                    .name(TEST_NAME).role("instructor")
                    .passwordHash("hashed").build();

            when(userRepository.existsByEmail(TEST_EMAIL)).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("hashed");
            when(userRepository.save(any(User.class))).thenReturn(instructorUser);
            when(cognitoService.authenticateUser(any(), any())).thenReturn(TEST_TOKEN);

            AuthResponse response = authService.signup(signupRequest);

            assertThat(response.getRole()).isEqualTo("instructor");
        }
    }

    @Nested
    @DisplayName("login()")
    class LoginTests {

        @Test
        @DisplayName("should return token when credentials are correct")
        void login_success() {
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(savedUser));
            when(passwordEncoder.matches(TEST_PASSWORD, savedUser.getPasswordHash())).thenReturn(true);
            when(cognitoService.authenticateUser(TEST_EMAIL, TEST_PASSWORD)).thenReturn(TEST_TOKEN);

            AuthResponse response = authService.login(loginRequest);

            assertThat(response).isNotNull();
            assertThat(response.getToken()).isEqualTo(TEST_TOKEN);
            assertThat(response.getEmail()).isEqualTo(TEST_EMAIL);
            assertThat(response.getUserId()).isEqualTo(TEST_USER_ID.toString());

            verify(userRepository).findByEmail(TEST_EMAIL);
            verify(passwordEncoder).matches(TEST_PASSWORD, savedUser.getPasswordHash());
            verify(cognitoService).authenticateUser(TEST_EMAIL, TEST_PASSWORD);
        }

        @Test
        @DisplayName("should throw UnauthorisedException when email not found")
        void login_userNotFound_throwsException() {
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(UnauthorisedException.class);

            verify(cognitoService, never()).authenticateUser(any(), any());
        }

        @Test
        @DisplayName("should throw UnauthorisedException when password is wrong")
        void login_wrongPassword_throwsException() {
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(savedUser));
            when(passwordEncoder.matches(TEST_PASSWORD, savedUser.getPasswordHash())).thenReturn(false);

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(UnauthorisedException.class)
                    .hasMessageContaining("Invalid");

            verify(cognitoService, never()).authenticateUser(any(), any());
        }

        @Test
        @DisplayName("should propagate exception when Cognito authentication fails")
        void login_cognitoFails_throwsException() {
            when(userRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(savedUser));
            when(passwordEncoder.matches(TEST_PASSWORD, savedUser.getPasswordHash())).thenReturn(true);
            when(cognitoService.authenticateUser(TEST_EMAIL, TEST_PASSWORD))
                    .thenThrow(new UnauthorisedException("Cognito auth failed"));

            assertThatThrownBy(() -> authService.login(loginRequest))
                    .isInstanceOf(UnauthorisedException.class);
        }
    }
}
