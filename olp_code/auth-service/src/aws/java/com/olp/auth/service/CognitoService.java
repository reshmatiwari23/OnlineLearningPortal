package com.olp.auth.service;

import com.olp.common.exception.DuplicateResourceException;
import com.olp.common.exception.UnauthorisedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.Map;

/**
 * Real Cognito implementation — active on aws profile only.
 * Implements CognitoPort so AuthService can inject it without AWS SDK dependency.
 */
@Service
@Profile("!local")
@Slf4j
public class CognitoService implements CognitoPort {

    private final CognitoIdentityProviderClient cognitoClient;

    @Value("${cognito.user-pool-id}")
    private String userPoolId;

    @Value("${cognito.client-id}")
    private String clientId;

    public CognitoService(CognitoIdentityProviderClient cognitoClient) {
        this.cognitoClient = cognitoClient;
    }

    @Override
    public void createUser(String email, String name, String role, String password) {
        try {
            log.debug("Creating Cognito user for email: {}", email);

            AdminCreateUserRequest createRequest = AdminCreateUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .messageAction(MessageActionType.SUPPRESS)
                    .temporaryPassword(password)
                    .userAttributes(
                            AttributeType.builder().name("email").value(email).build(),
                            AttributeType.builder().name("email_verified").value("true").build(),
                            AttributeType.builder().name("name").value(name).build(),
                            AttributeType.builder().name("custom:role").value(role).build()
                    )
                    .build();

            cognitoClient.adminCreateUser(createRequest);

            AdminSetUserPasswordRequest passwordRequest = AdminSetUserPasswordRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .password(password)
                    .permanent(true)
                    .build();

            cognitoClient.adminSetUserPassword(passwordRequest);
            log.debug("Cognito user created: {}", email);

        } catch (UsernameExistsException e) {
            throw new DuplicateResourceException("Email already registered: " + email);
        } catch (CognitoIdentityProviderException e) {
            log.error("Cognito createUser failed: {}", e.getMessage());
            throw new RuntimeException("Failed to create user: " + e.getMessage(), e);
        }
    }

    @Override
    public String authenticateUser(String email, String password) {
        try {
            InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                    .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                    .clientId(clientId)
                    .authParameters(Map.of("USERNAME", email, "PASSWORD", password))
                    .build();

            InitiateAuthResponse response = cognitoClient.initiateAuth(authRequest);
            return response.authenticationResult().idToken();

        } catch (NotAuthorizedException | UserNotFoundException e) {
            throw new UnauthorisedException("Invalid email or password");
        } catch (CognitoIdentityProviderException e) {
            log.error("Cognito auth failed: {}", e.getMessage());
            throw new RuntimeException("Authentication failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void deleteUser(String email) {
        try {
            cognitoClient.adminDeleteUser(AdminDeleteUserRequest.builder()
                    .userPoolId(userPoolId).username(email).build());
        } catch (Exception e) {
            log.warn("Cognito rollback delete failed for {}: {}", email, e.getMessage());
        }
    }
}
