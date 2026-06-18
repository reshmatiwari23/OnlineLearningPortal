package com.olp.course.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.olp.course.entity.UploadStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Response body returned for all course endpoints.
 * aiSummary is included if Claude has generated it, null otherwise.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseResponse {

    private UUID id;
    private String title;
    private String description;
    private UUID instructorId;
    private String instructorName;
    private String videoUrl;          // full CloudFront URL, assembled in service
    private Integer videoDuration;    // seconds
    private String thumbnailUrl;
    private UploadStatus uploadStatus; // none | pending | processing | ready | failed
    private JsonNode aiSummary;       // null until Claude generates it
    private Boolean kbIngested;
    private Boolean isPublished;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
