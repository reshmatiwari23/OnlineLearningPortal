package com.olp.ai.dto;

import lombok.*;
import java.util.UUID;

/** A single search result from semantic course search */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResponse {
    private UUID courseId;
    private String courseTitle;
    private String instructorName;
    private Double similarityScore;
    private String matchedExcerpt;   // relevant snippet from the course transcript
}
