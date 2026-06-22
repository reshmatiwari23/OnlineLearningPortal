package com.olp.progress.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.progress.dto.ProgressEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.List;

/**
 * SQS Consumer — polls olp-progress-writes-queue and persists to DB.
 * Runs every 5 seconds on aws profile only.
 * This completes the async write pipeline:
 *   Browser → REST API → Redis + SQS publish → SQS Consumer → RDS PostgreSQL
 */
@Service
@Profile("!local")
@RequiredArgsConstructor
@Slf4j
public class SqsProgressConsumer {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final ProgressService progressService;

    @Value("${aws.sqs.progress-queue-url}")
    private String queueUrl;

    @Scheduled(fixedDelay = 5000) // Poll every 5 seconds
    public void pollQueue() {
        try {
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(10)  // Process up to 10 at a time
                    .waitTimeSeconds(2)        // Long polling — reduces empty receives
                    .visibilityTimeout(30)     // 30s to process before retry
                    .build();

            List<Message> messages = sqsClient.receiveMessage(receiveRequest).messages();

            if (!messages.isEmpty()) {
                log.info("Processing {} progress events from SQS", messages.size());
            }

            for (Message message : messages) {
                try {
                    ProgressEvent event = objectMapper.readValue(
                            message.body(), ProgressEvent.class);

                    // Persist to DB
                    progressService.persistProgressEvent(event);

                    // Delete from queue after successful processing
                    sqsClient.deleteMessage(DeleteMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .receiptHandle(message.receiptHandle())
                            .build());

                    log.debug("Progress persisted and deleted from SQS: userId={}, courseId={}, percent={}",
                            event.getUserId(), event.getCourseId(), event.getPercentComplete());

                } catch (Exception e) {
                    log.error("Failed to process SQS message: {}", e.getMessage());
                    // Message will become visible again after visibilityTimeout
                }
            }

        } catch (Exception e) {
            log.error("SQS poll failed: {}", e.getMessage());
        }
    }
}
