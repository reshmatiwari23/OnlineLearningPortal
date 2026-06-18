package com.olp.auth.service;

import com.olp.common.exception.DuplicateResourceException;
import com.olp.common.exception.UnauthorisedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.Map;

/**
 * Handles all Amazon Cognito operations.
 *
 * Cognito is used for two things:
 * 1. Creating a user record in the User Pool (so Cognito knows they exist)
 * 2. Authenticating the user and getting a JWT token
 *
 * The JWT Cognito issues is RS256-signed.
 * API Gateway's Lambda Authoriser validates it on every protected request.
 */
@Service
@Slf4j
public class CognitoService {

    private final CognitoIdentityProviderClient cognitoClient;

    // Required constructor — Spring injects the real client on AWS,
    // MockCognitoService passes null (it never calls super methods)
    public CognitoService(CognitoIdentityProviderClient cognitoClient) {
        this.cognitoClient = cognitoClient;
    }

    @Value("${cognito.user-pool-id}")
    private String userPoolId;

    @Value("${cognito.client-id}")
    private String clientId;

    /**
     * Creates a user in the Cognito User Pool.
     * Called during signup after the user is saved to RDS.
     *
     * Sets email_verified = true automatically (we trust our own signup flow).
     * Sets custom:role attribute so the JWT contains the user's role.
     *
     * @param email    user's email address
     * @param name     user's full name
     * @param role     "user" or "instructor"
     * @param password the plain password (Cognito hashes it separately from BCrypt)
     */
    public void createUser(String email, String name, String role, String password) {
        try {
            log.debug("Creating Cognito user for email: {}", email);

            // Step 1: Create the user account in Cognito
            AdminCreateUserRequest createRequest = AdminCreateUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .messageAction(MessageActionType.SUPPRESS) // don't send welcome email (we handle it)
                    .temporaryPassword(password)
                    .userAttributes(
                            AttributeType.builder().name("email").value(email).build(),
                            AttributeType.builder().name("email_verified").value("true").build(),
                            AttributeType.builder().name("name").value(name).build(),
                            AttributeType.builder().name("custom:role").value(role).build()
                    )
                    .build();

            cognitoClient.adminCreateUser(createRequest);

            // Step 2: Set permanent password so user doesn't need to change it on first login
            AdminSetUserPasswordRequest passwordRequest = AdminSetUserPasswordRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .password(password)
                    .permanent(true)
                    .build();

            cognitoClient.adminSetUserPassword(passwordRequest);

            log.debug("Cognito user created successfully for email: {}", email);

        } catch (UsernameExistsException e) {
            throw new DuplicateResourceException("Email already registered: " + email);
        } catch (CognitoIdentityProviderException e) {
            log.error("Cognito createUser failed for email {}: {}", email, e.getMessage());
            throw new RuntimeException("Failed to create user account: " + e.getMessage(), e);
        }
    }

    /**
     * Authenticates a user with Cognito and returns the JWT ID token.
     * This token is what gets returned to the browser and sent on every API call.
     *
     * @param email    user's email
     * @param password user's plain password
     * @return Cognito IdToken (RS256 signed JWT)
     */
    public String authenticateUser(String email, String password) {
        try {
            log.debug("Authenticating Cognito user: {}", email);

            InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                    .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                    .clientId(clientId)
                    .authParameters(Map.of(
                            "USERNAME", email,
                            "PASSWORD", password
                    ))
                    .build();

            InitiateAuthResponse authResponse = cognitoClient.initiateAuth(authRequest);
            String token = authResponse.authenticationResult().idToken();

            log.debug("Cognito authentication successful for: {}", email);
            return token;

        } catch (NotAuthorizedException e) {
            throw new UnauthorisedException("Invalid email or password");
        } catch (UserNotFoundException e) {
            throw new UnauthorisedException("Invalid email or password");
        } catch (CognitoIdentityProviderException e) {
            log.error("Cognito authentication failed for {}: {}", email, e.getMessage());
            throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes a user from Cognito.
     * Called as a rollback if RDS save succeeds but something else fails.
     */
    public void deleteUser(String email) {
        try {
            AdminDeleteUserRequest request = AdminDeleteUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .build();
            cognitoClient.adminDeleteUser(request);
            log.debug("Cognito user deleted (rollback): {}", email);
        } catch (Exception e) {
            // Log but do not throw — this is a best-effort rollback
            log.warn("Failed to delete Cognito user during rollback for {}: {}", email, e.getMessage());
        }
    }
}
