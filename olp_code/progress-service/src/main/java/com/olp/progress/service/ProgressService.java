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
import java.util.UUID;
import java.util.stream.Collectors;
 
@Service
@RequiredArgsConstructor
@Slf4j
public class ProgressService {
 
    private final VideoProgressRepository progressRepository;
    private final RedisProgressService redisProgressService;
    private final SqsPort sqsPort;
 
    public ProgressResponse updateProgress(UUID courseId,
                                           String userId,
                                           UpdateProgressRequest request) {
 
        UUID userUuid = UUID.fromString(userId);
        int percent = calculatePercent(
                request.getCurrentTimeSecs(), request.getDurationSecs());
 
        // Step 1 — write to Redis (best effort — Redis may not be running locally)
        try {
            redisProgressService.writeProgress(userUuid, courseId, percent);
        } catch (Exception e) {
            log.debug("Redis write skipped (Redis not available locally): {}", e.getMessage());
        }
 
        // Step 2 — publish to SQS/mock (this writes to H2 locally)
        ProgressEvent event = ProgressEvent.builder()
                .userId(userUuid)
                .courseId(courseId)
                .currentTimeSecs(request.getCurrentTimeSecs())
                .durationSecs(request.getDurationSecs())
                .percentComplete(percent)
                .eventTime(LocalDateTime.now())
                .build();
 
        try {
            sqsPort.publish(event);
        } catch (Exception e) {
            log.warn("SQS publish failed: {} — persisting directly to H2", e.getMessage());
            // Fallback — write directly to H2 if mock SQS fails
            persistProgressEvent(event);
        }
 
        return ProgressResponse.builder()
                .userId(userUuid)
                .courseId(courseId)
                .currentTimeSecs(request.getCurrentTimeSecs())
                .durationSecs(request.getDurationSecs())
                .percentComplete(percent)
                .completed(percent >= 100)
                .source("redis")
                .build();
    }
 
    public ProgressResponse getProgress(UUID courseId, String userId) {
        UUID userUuid = UUID.fromString(userId);
 
        // Try Redis first (may fail locally)
        try {
            Integer cached = redisProgressService.readProgress(userUuid, courseId);
            if (cached != null) {
                VideoProgress db = progressRepository
                        .findByUserIdAndCourseId(userUuid, courseId)
                        .orElse(null);
                return ProgressResponse.builder()
                        .userId(userUuid)
                        .courseId(courseId)
                        .currentTimeSecs(db != null ? db.getCurrentTimeSecs() : 0)
                        .durationSecs(db != null ? db.getDurationSecs() : 0)
                        .percentComplete(cached)
                        .completed(cached >= 100)
                        .source("redis")
                        .build();
            }
        } catch (Exception e) {
            log.debug("Redis read skipped: {}", e.getMessage());
        }
 
        // Fall back to H2
        VideoProgress progress = progressRepository
                .findByUserIdAndCourseId(userUuid, courseId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Progress", "courseId", courseId));
 
        return toResponse(progress, "database");
    }
 
    public List<ProgressResponse> getAllProgressForUser(String userId) {
        UUID userUuid = UUID.fromString(userId);
        return progressRepository.findAllByUserId(userUuid)
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
        progress.setLastUpdatedAt(
                event.getEventTime() != null ? event.getEventTime() : LocalDateTime.now());
 
        if (event.getPercentComplete() >= 100 && progress.getCompletedAt() == null) {
            progress.setCompletedAt(LocalDateTime.now());
        }
 
        progressRepository.save(progress);
        log.debug("Progress saved: userId={}, courseId={}, percent={}",
                event.getUserId(), event.getCourseId(), event.getPercentComplete());
    }
 
    private int calculatePercent(int current, int duration) {
        if (duration <= 0) return 0;
        return Math.min(100, (int) Math.round((current * 100.0) / duration));
    }
 
    private ProgressResponse toResponse(VideoProgress p, String source) {
        boolean completed = p.getCompletedAt() != null;
        return ProgressResponse.builder()
                .userId(p.getUserId())
                .courseId(p.getCourseId())
                .currentTimeSecs(p.getCurrentTimeSecs())
                .durationSecs(p.getDurationSecs())
                .percentComplete(p.getPercentComplete())
                .completed(completed)
                .completedAt(p.getCompletedAt())
                .lastUpdatedAt(p.getLastUpdatedAt())
                .source(source)
                .build();
    }
}
 
 