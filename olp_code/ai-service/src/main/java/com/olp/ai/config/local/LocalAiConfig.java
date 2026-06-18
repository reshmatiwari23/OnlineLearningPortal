package com.olp.ai.config.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.*;
import software.amazon.awssdk.services.bedrockagentruntime.BedrockAgentRuntimeClient;
import software.amazon.awssdk.services.bedrockagentruntime.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Mock AWS clients for local development.
 * All Bedrock, DynamoDB, and SES calls return canned responses.
 * No AWS credentials or internet connection needed locally.
 */
@Configuration
@Profile("local")
@Slf4j
public class LocalAiConfig {

    private static final String MOCK_CLAUDE_RESPONSE = """
            {"content":[{"type":"text","text":"This is a mock response from Claude. \
            In production, this would be a real AI-generated answer based on the \
            course transcript content retrieved from OpenSearch Knowledge Base."}]}
            """;

    private static final String MOCK_TITAN_RESPONSE_PREFIX =
            "{\"embedding\":[";

    @Bean
    @Primary
    public BedrockRuntimeClient bedrockRuntimeClient() {
        log.info("[LOCAL MOCK] Using mock BedrockRuntimeClient");

        return new BedrockRuntimeClient() {

            @Override
            public InvokeModelResponse invokeModel(InvokeModelRequest request) {
                String modelId = request.modelId();
                log.info("[LOCAL MOCK] Bedrock invokeModel: {}", modelId);

                String responseBody;

                if (modelId.contains("titan-embed")) {
                    // Return a fake 1536-dim embedding vector
                    Random rng = new Random(request.body().asByteArray().length);
                    StringBuilder sb = new StringBuilder(MOCK_TITAN_RESPONSE_PREFIX);
                    for (int i = 0; i < 1536; i++) {
                        sb.append(String.format("%.6f", rng.nextDouble() * 2 - 1));
                        if (i < 1535) sb.append(",");
                    }
                    sb.append("]}");
                    responseBody = sb.toString();
                } else {
                    responseBody = MOCK_CLAUDE_RESPONSE;
                }

                return InvokeModelResponse.builder()
                        .body(SdkBytes.fromUtf8String(responseBody))
                        .contentType("application/json")
                        .build();
            }

            @Override
            public InvokeModelWithResponseStreamResponse invokeModelWithResponseStream(
                    InvokeModelWithResponseStreamRequest request,
                    InvokeModelWithResponseStreamResponseHandler handler) {
                log.info("[LOCAL MOCK] Bedrock streaming: {}", request.modelId());
                // For local testing, streaming is not fully simulated
                // Use the non-streaming /ai/summary endpoint for local testing
                return InvokeModelWithResponseStreamResponse.builder().build();
            }

            @Override
            public String serviceName() { return "bedrock-runtime-mock"; }

            @Override
            public void close() { }
        };
    }

    @Bean
    @Primary
    public BedrockAgentRuntimeClient bedrockAgentRuntimeClient() {
        log.info("[LOCAL MOCK] Using mock BedrockAgentRuntimeClient");

        return new BedrockAgentRuntimeClient() {

            @Override
            public RetrieveResponse retrieve(RetrieveRequest request) {
                log.info("[LOCAL MOCK] KB retrieve for KB: {}", request.knowledgeBaseId());

                // Return 2 fake retrieved chunks
                RetrievedReference chunk1 = RetrievedReference.builder()
                        .content(RetrievalResultContent.builder()
                                .text("Mock transcript chunk 1: This course covers Spring Boot 3 fundamentals. " +
                                      "In this section we discuss auto-configuration and starter dependencies.")
                                .build())
                        .score(0.92)
                        .build();

                RetrievedReference chunk2 = RetrievedReference.builder()
                        .content(RetrievalResultContent.builder()
                                .text("Mock transcript chunk 2: The dependency injection pattern in Spring " +
                                      "allows loose coupling between components using @Autowired annotations.")
                                .build())
                        .score(0.87)
                        .build();

                return RetrieveResponse.builder()
                        .retrievalResults(List.of(chunk1, chunk2))
                        .build();
            }

            @Override
            public String serviceName() { return "bedrock-agent-runtime-mock"; }

            @Override
            public void close() { }
        };
    }

    @Bean
    @Primary
    public DynamoDbClient dynamoDbClient() {
        log.info("[LOCAL MOCK] Using mock DynamoDbClient");
        return DynamoDbClient.builder()
                .region(software.amazon.awssdk.regions.Region.US_EAST_1)
                .credentialsProvider(
                        software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider.create())
                .endpointOverride(java.net.URI.create("http://localhost:8000"))
                .build();
    }

    @Bean
    @Primary
    public SesClient sesClient() {
        log.info("[LOCAL MOCK] Using mock SesClient");

        return new SesClient() {
            @Override
            public SendEmailResponse sendEmail(SendEmailRequest request) {
                log.info("[LOCAL MOCK] SES email would be sent to: {}",
                        request.destination().toAddresses());
                return SendEmailResponse.builder()
                        .messageId("mock-message-id-" + System.currentTimeMillis())
                        .build();
            }

            @Override
            public String serviceName() { return "ses-mock"; }

            @Override
            public void close() { }
        };
    }
}
