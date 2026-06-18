package com.olp.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

/**
 * AWS SDK beans for auth-service.
 * Active on all profiles EXCEPT local.
 *
 * Credentials are resolved automatically by DefaultCredentialsProvider:
 *   - Local machine: reads from ~/.aws/credentials (set up via aws configure)
 *   - ECS Fargate:   reads from ECS task IAM role (olp-auth-task-role)
 *     No credentials need to be hardcoded anywhere.
 */
@Configuration
@Profile("!local")
public class AwsConfig {

    @Value("${cognito.region:ap-south-1}")
    private String cognitoRegion;

    @Bean
    public CognitoIdentityProviderClient cognitoIdentityProviderClient() {
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(cognitoRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
