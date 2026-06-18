package com.olp.progress.config.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.progress.dto.ProgressEvent;
import com.olp.progress.service.ProgressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.util.UUID;

/**
 * Local development configuration for progress-service.
 *
 * Provides:
 * 1. Mock SqsClient — logs messages instead of sending to real SQS.
 *    Also immediately processes the message to simulate the SQS consumer,
 *    so RDS gets updated during local testing.
 *
 * 2. Mock StringRedisTemplate — uses a simple ConcurrentHashMap
 *    instead of real Redis. No Redis installation needed locally.
 */
@Configuration
@Profile("local")
@Slf4j
public class LocalConfig {

    /**
     * Mock SQS client.
     * On local profile: logs the message body instead of sending to AWS.
     * Also immediately calls progressService.persistProgressEvent()
     * to simulate the SQS consumer — so you can test the full flow locally.
     */
    @Bean
    @Primary
    public SqsClient sqsClient(ObjectMapper objectMapper,
                                ProgressService progressService) {
        return new SqsClient() {

            @Override
            public SendMessageResponse sendMessage(SendMessageRequest request) {
                try {
                    String body = request.messageBody();
                    log.info("[LOCAL MOCK] SQS message would be sent: {}", body);

                    // Immediately process the event to simulate the consumer
                    ProgressEvent event = objectMapper.readValue(body, ProgressEvent.class);
                    progressService.persistProgressEvent(event);
                    log.info("[LOCAL MOCK] Progress event immediately persisted to RDS (simulating consumer)");

                } catch (Exception e) {
                    log.warn("[LOCAL MOCK] Could not process SQS message locally: {}", e.getMessage());
                }

                return SendMessageResponse.builder()
                        .messageId(UUID.randomUUID().toString())
                        .build();
            }

            @Override
            public String serviceName() { return "sqs-mock"; }

            @Override
            public void close() { /* nothing to close */ }
        };
    }
}
