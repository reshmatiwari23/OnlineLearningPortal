package com.olp.progress.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA entity for the video_progress table.
 *
 * IMPORTANT: This table is written to ASYNCHRONOUSLY via SQS.
 * Do not read from this table in the hot path (progress updates).
 * For real-time progress reads, the service checks Redis first.
 * This table is the source of truth when Redis has no cached value.
 *
 * Write flow:
 *   Video.js timeupdate → POST /api/progress
 *   → ProgressService writes to Redis (sync, fast)
 *   → ProgressService sends SQS message (async, fire-and-forget)
 *   → SQS consumer (ProgressConsumer) reads messages in batches
 *   → ProgressConsumer upserts into this table
 */
@Entity
@Table(name = "video_progress",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_progress",
           columnNames = {"user_id", "course_id"}
       ))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VideoProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "course_id", nullable = false)
    private UUID courseId;

    // Current playback position in seconds from Video.js
    @Column(name = "current_time_secs", nullable = false)
    @Builder.Default
    private Integer currentTimeSecs = 0;

    // Total video duration — copied from courses table to avoid cross-service join
    @Column(name = "duration_secs", nullable = false)
    @Builder.Default
    private Integer durationSecs = 0;

    // Pre-calculated: ROUND((currentTimeSecs / durationSecs) * 100)
    // Stored so reads never need to calculate
    @Column(name = "percent_complete", nullable = false)
    @Builder.Default
    private Integer percentComplete = 0;

    // Timestamp of the last Video.js event (not when we wrote to RDS)
    @Column(name = "last_updated_at", nullable = false)
    private LocalDateTime lastUpdatedAt;

    // Set when percentComplete reaches 100 — triggers enrollment completion
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @PrePersist
    protected void onCreate() {
        if (lastUpdatedAt == null) lastUpdatedAt = LocalDateTime.now();
    }
}
