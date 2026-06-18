package com.olp.ai.dto;

import lombok.*;

/**
 * A citation links a part of Claude's answer back to a specific
 * chunk of the course transcript, including the video timestamp.
 *
 * The learner can click the timestamp to jump to that moment in the video.
 * Citations build trust — the learner can verify the AI answer
 * against what the instructor actually said.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Citation {

    // The OpenSearch chunk ID
    private String chunkId;

    // Video timestamp in seconds — learner can seek to this position
    private Integer timestampSeconds;

    // Cosine similarity score (0.0 - 1.0) — how relevant this chunk was
    private Double similarityScore;

    // Short excerpt from the transcript chunk (not the full chunk)
    private String excerpt;
}
