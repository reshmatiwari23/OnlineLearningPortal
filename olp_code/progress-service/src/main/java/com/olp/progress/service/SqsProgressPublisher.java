package com.olp.progress.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.progress.dto.ProgressEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * Publishes progress update events to the SQS queue for async RDS writes.
 *
 * This is called AFTER the Redis write succeeds.
 * It is fire-and-forget — if SQS is temporarily unavailable,
 * the Redis write already succeeded and the 200 response was already sent.
 *
 * The SQS consumer (in a separate Lambda or scheduled task) will:
 *   1. Receive the message
 *   2. Upsert into video_progress table in RDS
 *   3. Delete the message from the queue
 *
 * SQS guarantees at-least-once delivery.
 * The consumer uses UPSERT (INSERT ... ON CONFLICT UPDATE) to handle duplicates.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SqsProgressPublisher {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.progress-queue-url}")
    private String queueUrl;

    /**
     * Publish a progress event to SQS.
     * Errors are logged but NOT rethrown — this is async, best-effort.
     */
    public void publish(ProgressEvent event) {
        try {
            String messageBody = objectMapper.writeValueAsString(event);

            SendMessageRequest request = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    // Use userId+courseId as deduplication key
                    // (only relevant for FIFO queues, but good practice)
                    .messageGroupId(event.getUserId() + ":" + event.getCourseId())
                    .build();

            sqsClient.sendMessage(request);

            log.debug("SQS message sent: userId={}, courseId={}, percent={}",
                    event.getUserId(), event.getCourseId(), event.getPercentComplete());

        } catch (JsonProcessingException e) {
            log.error("Failed to serialise progress event to JSON: {}", e.getMessage());
        } catch (Exception e) {
            // Never throw — the Redis write already succeeded
            // The SQS message is best-effort
            log.error("Failed to send SQS message for userId={}, courseId={}: {}",
                    event.getUserId(), event.getCourseId(), e.getMessage());
        }
    }
}
