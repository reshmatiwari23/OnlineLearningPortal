package com.olp.progress.service;

import com.olp.common.exception.ResourceNotFoundException;
import com.olp.progress.dto.ProgressEvent;
import com.olp.progress.dto.ProgressResponse;
import com.olp.progress.dto.UpdateProgressRequest;
import com.olp.progress.entity.VideoProgress;
import com.olp.progress.repository.VideoProgressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core progress tracking service.
 *
 * Write path (hot):
 *   1. Write to Redis (sync, sub-millisecond)
 *   2. Publish to SQS via SqsPort (async, fire-and-forget)
 *
 * Read path:
 *   1. Check Redis (fast)
 *   2. Fall back to RDS if not in cache
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProgressService {

    private final VideoProgressRepository progressRepository;
    private final RedisProgressService redisProgressService;
    private final SqsPort sqsPort;   // injected — real SQS or mock depending on profile

    public ProgressResponse updateProgress(UUID courseId,
                                           String userId,
                                           UpdateProgressRequest request) {
        int percent = calculatePercent(
                request.getCurrentTimeSecs(), request.getDurationSecs());

        // Step 1 — write to Redis (synchronous, fast)
        redisProgressService.saveProgress(userId, courseId.toString(), percent);

        // Step 2 — publish to SQS (async, fire-and-forget)
        ProgressEvent event = ProgressEvent.builder()
                .userId(userId)
                .courseId(courseId)
                .currentTimeSecs(request.getCurrentTimeSecs())
                .durationSecs(request.getDurationSecs())
                .percentComplete(percent)
                .completed(percent >= 100)
                .eventTime(LocalDateTime.now())
                .build();

        sqsPort.publish(event);

        return ProgressResponse.builder()
                .userId(UUID.fromString(userId))
                .courseId(courseId)
                .currentTimeSecs(request.getCurrentTimeSecs())
                .durationSecs(request.getDurationSecs())
                .percentComplete(percent)
                .completed(percent >= 100)
                .source("redis")
                .build();
    }

    public ProgressResponse getProgress(UUID courseId, String userId) {
        // Try Redis first
        Optional<Integer> cached = redisProgressService.getProgress(userId, courseId.toString());
        if (cached.isPresent()) {
            log.debug("Progress cache hit: userId={}, courseId={}", userId, courseId);
            VideoProgress dbProgress = progressRepository
                    .findByUserIdAndCourseId(userId, courseId).orElse(null);

            return ProgressResponse.builder()
                    .userId(UUID.fromString(userId))
                    .courseId(courseId)
                    .currentTimeSecs(dbProgress != null ? dbProgress.getCurrentTimeSecs() : 0)
                    .durationSecs(dbProgress != null ? dbProgress.getDurationSecs() : 0)
                    .percentComplete(cached.get())
                    .completed(cached.get() >= 100)
                    .source("redis")
                    .build();
        }

        // Fall back to RDS
        VideoProgress progress = progressRepository
                .findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Progress", "userId/courseId", userId + "/" + courseId));

        return toResponse(progress, "database");
    }

    public List<ProgressResponse> getAllProgressForUser(String userId) {
        return progressRepository.findAllByUserId(userId)
                .stream()
                .map(p -> toResponse(p, "database"))
                .collect(Collectors.toList());
    }

    public List<ProgressResponse> getAllProgressForCourse(UUID courseId) {
        return progressRepository.findAllByCourseId(courseId)
                .stream()
                .map(p -> toResponse(p, "database"))
                .collect(Collectors.toList());
    }

    @Transactional
    public void persistProgressEvent(ProgressEvent event) {
        VideoProgress progress = progressRepository
                .findByUserIdAndCourseId(event.getUserId(), event.getCourseId())
                .orElse(VideoProgress.builder()
                        .userId(event.getUserId())
                        .courseId(event.getCourseId())
                        .build());

        progress.setCurrentTimeSecs(event.getCurrentTimeSecs());
        progress.setDurationSecs(event.getDurationSecs());
        progress.setPercentComplete(event.getPercentComplete());
        progress.setCompleted(event.isCompleted());
        if (event.isCompleted() && progress.getCompletedAt() == null) {
            progress.setCompletedAt(LocalDateTime.now());
        }

        progressRepository.save(progress);
        log.debug("Progress persisted: userId={}, percent={}",
                event.getUserId(), event.getPercentComplete());
    }

    private int calculatePercent(int current, int duration) {
        if (duration <= 0) return 0;
        return Math.min(100, (int) Math.round((current * 100.0) / duration));
    }

    private ProgressResponse toResponse(VideoProgress p, String source) {
        return ProgressResponse.builder()
                .userId(UUID.fromString(p.getUserId()))
                .courseId(p.getCourseId())
                .currentTimeSecs(p.getCurrentTimeSecs())
                .durationSecs(p.getDurationSecs())
                .percentComplete(p.getPercentComplete())
                .completed(p.isCompleted())
                .completedAt(p.getCompletedAt())
                .lastUpdatedAt(p.getUpdatedAt())
                .source(source)
                .build();
    }
}
