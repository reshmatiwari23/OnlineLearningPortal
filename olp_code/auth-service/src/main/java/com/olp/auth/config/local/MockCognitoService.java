package com.olp.auth.config.local;

import com.olp.auth.service.CognitoService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Mock replacement for CognitoService — active ONLY on the local profile.
 *
 * When running locally with spring.profiles.active=local:
 *   - createUser()       → logs a message, does nothing (no AWS call)
 *   - authenticateUser() → returns a fake JWT token string
 *   - deleteUser()       → logs a message, does nothing
 *
 * This means you can test signup and login locally without:
 *   - An internet connection
 *   - A real Cognito User Pool
 *   - Any AWS credentials
 *
 * On AWS ECS (production profile), the real CognitoService is used instead.
 *
 * @Primary ensures Spring picks this bean over the real CognitoService
 *          when both are on the classpath in the local profile.
 */
@Service
@Primary
@Profile("local")
@Slf4j
public class MockCognitoService extends CognitoService {

    // Constructor injection not needed — we override all methods
    public MockCognitoService() {
        super(null);  // CognitoService needs a client — pass null since we never call super
    }

    /**
     * Simulates creating a user in Cognito.
     * Locally: just logs and returns without doing anything.
     */
    @Override
    public void createUser(String email, String name, String role, String password) {
        log.info("[LOCAL MOCK] Cognito createUser called for: {} (role: {}) — skipping real AWS call", email, role);
    }

    /**
     * Simulates authenticating with Cognito.
     * Locally: returns a fake JWT token so the signup/login flow completes.
     *
     * The returned string looks like a real JWT but is not cryptographically signed.
     * It is only used locally — on AWS the real Cognito token is used.
     */
    @Override
    public String authenticateUser(String email, String password) {
        log.info("[LOCAL MOCK] Cognito authenticateUser called for: {} — returning fake token", email);

        // Return a fake token that looks like a JWT
        // The frontend will store this but it will not work with real API Gateway
        // That is fine — locally we test the auth-service in isolation
        return "local.mock.token." + email.replace("@", "_").replace(".", "_") + ".fake_signature";
    }

    /**
     * Simulates deleting a user from Cognito.
     * Locally: just logs and returns.
     */
    @Override
    public void deleteUser(String email) {
        log.info("[LOCAL MOCK] Cognito deleteUser called for: {} — skipping real AWS call", email);
    }
}
