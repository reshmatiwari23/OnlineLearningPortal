package com.olp.ai.dto;

import lombok.*;
import java.util.List;
import java.util.UUID;

// ── Recommendations ───────────────────────────────────────────────

/**
 * A single course recommendation with a personalised reason.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationResponse {

    private UUID courseId;
    private String courseTitle;
    private String instructorName;

    // Claude-generated personalised reason for this recommendation
    // e.g. "Based on your study of concurrency and distributed systems,
    //        this course on Kafka will naturally extend your knowledge"
    private String aiReason;

    // Cosine similarity score between user profile and course vectors
    private Double similarityScore;
}
