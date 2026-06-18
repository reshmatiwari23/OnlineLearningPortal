package com.olp.progress.service;

import com.olp.common.exception.ResourceNotFoundException;
import com.olp.progress.dto.ProgressEvent;
import com.olp.progress.dto.ProgressResponse;
import com.olp.progress.dto.UpdateProgressRequest;
import com.olp.progress.entity.VideoProgress;
import com.olp.progress.repository.VideoProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core business logic for video progress tracking.
 *
 * UPDATE flow (called every 5 seconds per active viewer):
 *   1. Calculate percent complete
 *   2. Write to Redis (synchronous — this is what makes the response fast)
 *   3. Publish to SQS (asynchronous — RDS write happens later)
 *   4. Return 200 immediately
 *
 * READ flow:
 *   1. Check Redis first (sub-millisecond)
 *   2. If Redis miss → read from RDS (source of truth)
 *   3. Return progress with source indicator ("redis" or "database")
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProgressService {

    private final VideoProgressRepository progressRepository;
    private final RedisProgressService redisService;
    private final SqsProgressPublisher sqsPublisher;

    @Value("${progress.completion-threshold:100}")
    private int completionThreshold;

    // ── Update progress (HOT PATH) ─────────────────────────────

    /**
     * Updates progress for a learner watching a video.
     * Called every 5 seconds by Video.js.
     * Returns in <100ms — Redis write is synchronous, SQS is async.
     */
    public ProgressResponse updateProgress(UUID courseId,
                                           String userId,
                                           UpdateProgressRequest request) {

        UUID userUuid = UUID.fromString(userId);

        // Step 1: Calculate percent complete
        // Guard against division by zero and values over 100
        int percent = 0;
        if (request.getDurationSecs() > 0) {
            percent = (int) Math.min(100,
                Math.round((request.getCurrentTimeSecs() * 100.0)
                           / request.getDurationSecs()));
        }

        log.debug("Progress update: userId={}, courseId={}, percent={}%",
                userId, courseId, percent);

        // Step 2: Write to Redis SYNCHRONOUSLY
        // This is why the endpoint can handle 2000 writes/sec —
        // Redis handles this volume trivially
        redisService.writeProgress(userUuid, courseId, percent);

        // Step 3: Publish to SQS ASYNCHRONOUSLY (fire-and-forget)
        // The response is returned BEFORE this message is processed
        ProgressEvent event = ProgressEvent.builder()
                .userId(userUuid)
                .courseId(courseId)
                .currentTimeSecs(request.getCurrentTimeSecs())
                .durationSecs(request.getDurationSecs())
                .percentComplete(percent)
                .eventTime(LocalDateTime.now())
                .build();

        sqsPublisher.publish(event);

        // Step 4: Return immediately with the calculated values
        // We do NOT wait for the RDS write
        return ProgressResponse.builder()
                .userId(userUuid)
                .courseId(courseId)
                .currentTimeSecs(request.getCurrentTimeSecs())
                .durationSecs(request.getDurationSecs())
                .percentComplete(percent)
                .completed(percent >= completionThreshold)
                .lastUpdatedAt(LocalDateTime.now())
                .source("redis")
                .build();
    }

    // ── Read progress ──────────────────────────────────────────

    /**
     * Reads progress for a specific user+course combination.
     * Checks Redis first — falls back to RDS on cache miss.
     */
    @Transactional(readOnly = true)
    public ProgressResponse getProgress(UUID courseId, String userId) {
        UUID userUuid = UUID.fromString(userId);

        // Try Redis first
        Integer cachedPercent = redisService.readProgress(userUuid, courseId);
        if (cachedPercent != null) {
            return ProgressResponse.builder()
                    .userId(userUuid)
                    .courseId(courseId)
                    .percentComplete(cachedPercent)
                    .completed(cachedPercent >= completionThreshold)
                    .source("redis")
                    .build();
        }

        // Redis miss — fall back to RDS
        VideoProgress progress = progressRepository
                .findByUserIdAndCourseId(userUuid, courseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Progress", "userId+courseId", userId + "+" + courseId));

        return toResponse(progress, "database");
    }

    /**
     * Get all progress records for a user — learner dashboard.
     */
    @Transactional(readOnly = true)
    public List<ProgressResponse> getAllProgressForUser(String userId) {
        return progressRepository
                .findAllByUserId(UUID.fromString(userId))
                .stream()
                .map(p -> toResponse(p, "database"))
                .collect(Collectors.toList());
    }

    /**
     * Get all learner progress for a course — instructor dashboard.
     */
    @Transactional(readOnly = true)
    public List<ProgressResponse> getAllProgressForCourse(UUID courseId) {
        return progressRepository
                .findAllByCourseId(courseId)
                .stream()
                .map(p -> toResponse(p, "database"))
                .collect(Collectors.toList());
    }

    // ── SQS Consumer write to RDS ──────────────────────────────

    /**
     * Called by the SQS consumer to persist a progress event to RDS.
     * Uses UPSERT pattern — inserts new record or updates existing one.
     * Safe to call multiple times for the same userId+courseId (idempotent).
     */
    @Transactional
    public void persistProgressEvent(ProgressEvent event) {
        try {
            Optional<VideoProgress> existing = progressRepository
                    .findByUserIdAndCourseId(event.getUserId(), event.getCourseId());

            VideoProgress progress;

            if (existing.isPresent()) {
                // UPDATE existing record — only if this event is newer
                progress = existing.get();
                if (event.getEventTime().isAfter(progress.getLastUpdatedAt())) {
                    progress.setCurrentTimeSecs(event.getCurrentTimeSecs());
                    progress.setDurationSecs(event.getDurationSecs());
                    progress.setPercentComplete(event.getPercentComplete());
                    progress.setLastUpdatedAt(event.getEventTime());

                    // Mark completed if threshold reached
                    if (event.getPercentComplete() >= completionThreshold
                            && progress.getCompletedAt() == null) {
                        progress.setCompletedAt(LocalDateTime.now());
                        log.info("Course completed! userId={}, courseId={}",
                                event.getUserId(), event.getCourseId());
                    }
                }
            } else {
                // INSERT new progress record
                progress = VideoProgress.builder()
                        .userId(event.getUserId())
                        .courseId(event.getCourseId())
                        .currentTimeSecs(event.getCurrentTimeSecs())
                        .durationSecs(event.getDurationSecs())
                        .percentComplete(event.getPercentComplete())
                        .lastUpdatedAt(event.getEventTime())
                        .completedAt(event.getPercentComplete() >= completionThreshold
                                ? LocalDateTime.now() : null)
                        .build();
            }

            progressRepository.save(progress);
            log.debug("RDS upsert: userId={}, courseId={}, percent={}",
                    event.getUserId(), event.getCourseId(), event.getPercentComplete());

        } catch (DataIntegrityViolationException e) {
            // Race condition: two SQS messages for the same user+course
            // processed simultaneously — safe to ignore, one will win
            log.warn("Concurrent progress upsert conflict for userId={}, courseId={} — ignoring",
                    event.getUserId(), event.getCourseId());
        }
    }

    // ── Helper ─────────────────────────────────────────────────

    private ProgressResponse toResponse(VideoProgress p, String source) {
        return ProgressResponse.builder()
                .userId(p.getUserId())
                .courseId(p.getCourseId())
                .currentTimeSecs(p.getCurrentTimeSecs())
                .durationSecs(p.getDurationSecs())
                .percentComplete(p.getPercentComplete())
                .completed(p.getCompletedAt() != null)
                .lastUpdatedAt(p.getLastUpdatedAt())
                .completedAt(p.getCompletedAt())
                .source(source)
                .build();
    }
}
