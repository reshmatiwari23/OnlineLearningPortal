package com.olp.progress.config.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.progress.dto.ProgressEvent;
import com.olp.progress.service.SqsPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Local config for progress-service.
 * Provides MockSqsPublisher which immediately persists progress to H2
 * instead of sending to real SQS. No AWS SDK needed.
 */
@Configuration
@Profile("local")
@Slf4j
public class LocalConfig {

    @Bean
    public SqsPort sqsPort(ObjectMapper objectMapper,
                            com.olp.progress.service.ProgressService progressService) {
        return event -> {
            try {
                log.info("[LOCAL MOCK] SQS skipped — persisting directly to H2: userId={}",
                        event.getUserId());
                progressService.persistProgressEvent(event);
            } catch (Exception e) {
                log.warn("[LOCAL MOCK] Could not persist progress event: {}", e.getMessage());
            }
        };
    }
}
