package com.olp.progress.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response body for progress read/update endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressResponse {

    private UUID userId;
    private UUID courseId;
    private Integer currentTimeSecs;
    private Integer durationSecs;
    private Integer percentComplete;   // 0-100
    private boolean completed;         // true when percentComplete = 100
    private LocalDateTime lastUpdatedAt;
    private LocalDateTime completedAt;
    private String source;             // "redis" or "database" — for debugging
}
