package com.olp.ai.dto;

import lombok.*;
import java.util.UUID;

// ── Auto Course Summary ───────────────────────────────────────────

/**
 * Request to generate an AI summary for a course.
 * Called by the kb-ingestion Lambda after Transcribe completes.
 * Not exposed to the frontend — internal Lambda → course-service call.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SummaryRequest {

    private UUID courseId;
    private String courseTitle;

    // Full transcript text from Amazon Transcribe VTT output
    private String transcriptText;
}
