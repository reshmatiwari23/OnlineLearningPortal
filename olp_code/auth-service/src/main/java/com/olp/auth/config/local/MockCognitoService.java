package com.olp.auth.config.local;

import com.olp.auth.service.CognitoPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Mock Cognito — active on local profile only.
 * Implements CognitoPort directly (no AWS SDK needed).
 * Returns fake responses so local dev works without any AWS connection.
 */
@Service
@Primary
@Profile("local")
@Slf4j
public class MockCognitoService implements CognitoPort {

    @Override
    public void createUser(String email, String name, String role, String password) {
        log.info("[LOCAL MOCK] Cognito createUser: {} (role: {}) — skipped", email, role);
    }

    @Override
    public String authenticateUser(String email, String password) {
        log.info("[LOCAL MOCK] Cognito authenticateUser: {} — returning fake token", email);
        return "local.mock.token." + email.replace("@", "_").replace(".", "_");
    }

    @Override
    public void deleteUser(String email) {
        log.info("[LOCAL MOCK] Cognito deleteUser: {} — skipped", email);
    }
}
