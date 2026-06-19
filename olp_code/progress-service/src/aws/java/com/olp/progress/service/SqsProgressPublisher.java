package com.olp.progress.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.progress.dto.ProgressEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Real SQS publisher — active on aws profile only.
 * Implements SqsPort so ProgressService can inject it without AWS SDK dependency.
 */
@Service
@Profile("!local")
@RequiredArgsConstructor
@Slf4j
public class SqsProgressPublisher implements SqsPort {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.progress-queue-url}")
    private String queueUrl;

    @Override
    public void publish(ProgressEvent event) {
        try {
            String messageBody = objectMapper.writeValueAsString(event);
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .build());
            log.debug("SQS message sent: userId={}, percent={}",
                    event.getUserId(), event.getPercentComplete());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialise progress event: {}", e.getMessage());
        } catch (Exception e) {
            log.error("SQS publish failed for userId={}: {}", event.getUserId(), e.getMessage());
        }
    }
}
