package com.olp.ai.dto;

import lombok.*;
import java.util.UUID;

// ── Progress Nudge ────────────────────────────────────────────────

/**
 * Request to generate and send a progress nudge to a stalled learner.
 * Called by the daily EventBridge scheduler (not by the frontend).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NudgeRequest {

    private String userId;
    private String learnerName;
    private String learnerEmail;
    private UUID courseId;
    private String courseTitle;
    private Integer percentComplete;     // current progress 0-100
    private Integer daysSinceLastWatch;  // how many days inactive
}
