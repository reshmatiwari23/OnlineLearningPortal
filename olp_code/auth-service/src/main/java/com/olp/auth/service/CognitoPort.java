package com.olp.auth.service;

/**
 * Interface for Cognito operations.
 * CognitoService implements this with real AWS SDK (aws profile).
 * MockCognitoService implements this with fake responses (local profile).
 *
 * This interface lives in src/main so it compiles without the AWS SDK.
 * The AWS SDK dependency is only needed by CognitoService in src/aws.
 */
public interface CognitoPort {

    void createUser(String email, String name, String role, String password);

    String authenticateUser(String email, String password);

    void deleteUser(String email);
}
