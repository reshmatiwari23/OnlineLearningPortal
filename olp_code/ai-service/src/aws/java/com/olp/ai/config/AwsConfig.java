package com.olp.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * AWS SDK beans for ai-service.
 * Active on all profiles EXCEPT local.
 *
 * CRITICAL: ai-service is the ONLY service with Bedrock IAM permissions.
 * This is enforced via olp-ai-task-role in ECS.
 * All other task roles must NOT have AmazonBedrockFullAccess.
 *
 * Bedrock must be in us-east-1 — model access is managed there.
 * DynamoDB and SES use ap-south-1 (same region as the rest).
 *
 * Credentials:
 *   Local (if used): reads from ~/.aws/credentials
 *   ECS Fargate:     reads from olp-ai-task-role automatically
 */
@Configuration
@Profile("!local")
public class AwsConfig {

    @Value("${bedrock.region:us-east-1}")
    private String bedrockRegion;

    @Value("${aws.region:ap-south-1}")
    private String awsRegion;

    /**
     * BedrockRuntimeClient — for direct Claude and Titan API calls.
     * Region: us-east-1 (where Bedrock model access was approved)
     */
    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        return BedrockRuntimeClient.builder()
                .region(Region.of(bedrockRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * BedrockAgentRuntimeClient — for Knowledge Base RAG retrieval.
     * Calls retrieve() to fetch relevant transcript chunks from OpenSearch.
     * Region: us-east-1 (same as Bedrock Runtime)
     */
    @Bean
    public BedrockAgentRuntimeClient bedrockAgentRuntimeClient() {
        return BedrockAgentRuntimeClient.builder()
                .region(Region.of(bedrockRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * DynamoDbClient — stores AI chat conversation history.
     * Table: olp-ai-sessions (TTL 24h set on each item)
     * Region: ap-south-1
     */
    @Bean
    public DynamoDbClient dynamoDbClient() {
        return DynamoDbClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * SesClient — sends progress nudge emails to learners.
     * From address (learning@olp.example.com) must be verified in SES.
     * Region: ap-south-1
     */
    @Bean
    public SesClient sesClient() {
        return SesClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }
}
