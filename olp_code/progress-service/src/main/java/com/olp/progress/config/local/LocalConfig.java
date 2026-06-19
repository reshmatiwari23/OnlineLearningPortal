package com.olp.progress.config.local;
 
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olp.progress.entity.VideoProgress;
import com.olp.progress.repository.VideoProgressRepository;
import com.olp.progress.service.SqsPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
 
import java.time.LocalDateTime;
 
/**
 * Local config for progress-service.
 *
 * Provides mock SqsPort that writes directly to H2 instead of SQS.
 *
 * IMPORTANT: Injects VideoProgressRepository directly — NOT ProgressService.
 * Injecting ProgressService would create a circular dependency:
 *   ProgressService → SqsPort → ProgressService (cycle!)
 *
 * By injecting the repository directly we break the cycle:
 *   ProgressService → SqsPort → VideoProgressRepository (no cycle)
 */
@Configuration
@Profile("local")
@Slf4j
public class LocalConfig {
 
    @Bean
    public SqsPort sqsPort(VideoProgressRepository progressRepository,
                            ObjectMapper objectMapper) {
        return event -> {
            try {
                log.info("[LOCAL MOCK] SQS skipped — writing directly to H2: " +
                        "userId={}, courseId={}, percent={}",
                        event.getUserId(), event.getCourseId(), event.getPercentComplete());
 
                // Upsert directly into H2 — same logic as ProgressService.persistProgressEvent
                VideoProgress progress = progressRepository
                        .findByUserIdAndCourseId(event.getUserId(), event.getCourseId())
                        .orElse(VideoProgress.builder()
                                .userId(event.getUserId())
                                .courseId(event.getCourseId())
                                .build());
 
                progress.setCurrentTimeSecs(event.getCurrentTimeSecs());
                progress.setDurationSecs(event.getDurationSecs());
                progress.setPercentComplete(event.getPercentComplete());
                progress.setLastUpdatedAt(event.getEventTime() != null
                        ? event.getEventTime() : LocalDateTime.now());
 
                if (event.getPercentComplete() >= 100
                        && progress.getCompletedAt() == null) {
                    progress.setCompletedAt(LocalDateTime.now());
                }
 
                progressRepository.save(progress);
                log.debug("[LOCAL MOCK] Progress saved to H2: percent={}",
                        event.getPercentComplete());
 
            } catch (Exception e) {
                log.warn("[LOCAL MOCK] Could not persist progress event: {}", e.getMessage());
            }
        };
    }
}
 
 